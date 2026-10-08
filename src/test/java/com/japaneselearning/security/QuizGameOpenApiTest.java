package com.japaneselearning.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.quiz.player.dto.QuizSessionCreateRequest;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class QuizGameOpenApiTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mergedContractExposesOnlyTwoPlayerOperationsAndSafeResponses() throws Exception {
        JsonNode document = mapper.readTree(given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().asString());
        Set<String> paths = new HashSet<>();
        document.path("paths").fieldNames().forEachRemaining(path -> {
            if (path.startsWith("/api/v1/kanji-quiz")) {
                paths.add(path);
            }
        });
        assertEquals(Set.of("/api/v1/kanji-quiz/config", "/api/v1/kanji-quiz/sessions"), paths);
        for (String path : paths) {
            JsonNode operation = document.path("paths").path(path).path(path.endsWith("config") ? "get" : "post");
            assertEquals("Kanji Quiz", operation.path("tags").get(0).asText());
            assertTrue(operation.path("description").asText().contains("User or Admin"));
            assertTrue(operation.path("security").get(0).has("bearerAuth"));
            for (String status : List.of("200", "401", "403", "500")) {
                assertTrue(operation.path("responses").has(status));
            }
        }
        JsonNode properties = document.at("/components/schemas/QuizSessionCreatedResponse/properties");
        Set<String> fields = new HashSet<>();
        properties.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("sessionId", "status", "questionCount", "createdAt"), fields);
        JsonNode request = document.at("/components/schemas/QuizSessionCreateRequest");
        assertEquals(1, request.at("/properties/questionCount/minimum").asInt());
        assertEquals(100, request.at("/properties/questionCount/maximum").asInt());
        assertFalse(request.path("properties").has("userId"));
        JsonNode operation = document.path("paths").path("/api/v1/kanji-quiz/sessions").path("post");
        assertTrue(operation.path("responses").has("400"));
        assertTrue(operation.path("responses").has("409"));
        JsonNode examples = operation.at("/requestBody/content/application~1json/examples");
        for (String name : List.of("filtered", "unfiltered")) {
            var value = mapper.treeToValue(examples.path(name).path("value"), QuizSessionCreateRequest.class);
            assertTrue(value.questionCount() >= 1 && value.questionCount() <= 100);
        }
    }
}
