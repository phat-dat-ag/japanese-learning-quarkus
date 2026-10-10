package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.quiz.domain.QuizSessionStatus;
import com.japaneselearning.quiz.player.dto.QuizHistoryDetailResponse;
import com.japaneselearning.quiz.player.dto.QuizHistoryListResponse;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class QuizHistoryOpenApiTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mergedHistoryContractsDocumentSecurityPaginationAndIncompleteSessions() throws Exception {
        JsonNode document = document();
        for (String suffix : List.of("", "/{sessionId}")) {
            JsonNode operation =
                    document.path("paths").path("/api/v1/kanji-quiz/history" + suffix).path("get");
            assertFalse(operation.has("requestBody"));
            assertTrue(operation.path("security").get(0).has("bearerAuth"));
            assertEquals("Kanji Quiz", operation.path("tags").get(0).asText());
            for (String code : List.of("200", "400", "401", "403", "404", "500")) {
                assertTrue(operation.path("responses").has(code));
            }
            assertTrue(operation.path("description").asText().contains("Read-only"));
            assertTrue(operation.path("description").asText().contains("COMPLETED"));
            JsonNode parameters = operation.path("parameters");
            if (suffix.isEmpty()) {
                assertEquals(2, parameters.size());
                assertEquals("page", parameters.get(0).path("name").asText());
                assertEquals(0, parameters.get(0).at("/schema/default").asInt());
                assertEquals(0, parameters.get(0).at("/schema/minimum").asInt());
                assertEquals("size", parameters.get(1).path("name").asText());
                assertEquals(20, parameters.get(1).at("/schema/default").asInt());
                assertEquals(100, parameters.get(1).at("/schema/maximum").asInt());
            } else {
                assertEquals("sessionId", parameters.get(0).path("name").asText());
                assertTrue(parameters.get(0).path("required").asBoolean());
                assertTrue(
                        operation
                                .at("/responses/404/description")
                                .asText()
                                .contains("not COMPLETED"));
            }
        }
    }

    @Test
    void examplesAndSchemasMatchHistoryDtosWithoutChangingGameAnswerSecrecy() throws Exception {
        JsonNode document = document();
        JsonNode paths = document.path("paths");
        JsonNode schemas = document.at("/components/schemas");
        var page =
                mapper.treeToValue(
                        paths.path("/api/v1/kanji-quiz/history")
                                .at(
                                        "/get/responses/200/content/application~1json/examples/success/value/data"),
                        QuizHistoryListResponse.class);
        assertEquals(1, page.totalElements());
        assertEquals(QuizSessionStatus.COMPLETED, page.items().get(0).status());
        assertNull(page.items().get(0).levelId());
        assertNull(page.items().get(0).lessonId());
        var empty =
                mapper.treeToValue(
                        paths.path("/api/v1/kanji-quiz/history")
                                .at(
                                        "/get/responses/200/content/application~1json/examples/empty/value/data"),
                        QuizHistoryListResponse.class);
        assertTrue(empty.items().isEmpty());
        assertEquals(0, empty.totalPages());
        var detail =
                mapper.treeToValue(
                        paths.path("/api/v1/kanji-quiz/history/{sessionId}")
                                .at(
                                        "/get/responses/200/content/application~1json/examples/success/value/data"),
                        QuizHistoryDetailResponse.class);
        assertEquals(page.items().get(0), detail.session());
        assertEquals(4, detail.questions().get(0).options().size());
        assertTrue(detail.questions().get(0).correct());
        assertEquals(
                detail.questions().get(0).correctOptionId(),
                detail.questions().get(0).selectedOptionId());
        assertEquals(
                Set.of("session", "questions"),
                fields(schemas.at("/QuizHistoryDetailResponse/properties")));
        assertEquals(
                Set.of("items", "page", "size", "totalElements", "totalPages"),
                fields(schemas.at("/QuizHistoryListResponse/properties")));
        assertTrue(
                schemas.at("/QuizHistorySummary/properties/levelId/anyOf")
                        .toString()
                        .contains("null"));
        assertTrue(
                schemas.at("/QuizHistoryQuestion/properties/explanationVi/anyOf")
                        .toString()
                        .contains("null"));
        assertEquals(
                "#/components/schemas/QuizPlayerOption",
                schemas.at("/QuizHistoryQuestion/properties/options/items/$ref").asText());
        assertEquals(Set.of("id", "text"), fields(schemas.at("/QuizPlayerOption/properties")));
        assertFalse(schemas.at("/QuizPlayerQuestion/properties").has("correctOptionId"));
        assertFalse(schemas.at("/QuizPlayerQuestion/properties").has("explanationVi"));
    }

    private JsonNode document() throws Exception {
        return mapper.readTree(
                given().accept("application/json")
                        .get("/q/openapi")
                        .then()
                        .statusCode(200)
                        .extract()
                        .asString());
    }

    private Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }
}
