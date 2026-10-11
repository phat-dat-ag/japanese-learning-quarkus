package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse;
import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse.Classification;
import com.japaneselearning.quiz.player.dto.QuizProgressResponse;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class QuizProgressOpenApiTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void mergedContractsDeclareAuthenticationReadOnlyAggregationAndErrors() throws Exception {
        JsonNode document = document();
        for (String suffix : List.of("", "/breakdown")) {
            JsonNode operation =
                    document.path("paths").path("/api/v1/kanji-quiz/progress" + suffix).path("get");
            assertFalse(operation.has("requestBody"));
            assertEquals(0, operation.path("parameters").size());
            assertTrue(operation.path("security").get(0).has("bearerAuth"));
            assertEquals("Kanji Quiz", operation.path("tags").get(0).asText());
            for (String code : List.of("200", "401", "403", "500")) {
                assertTrue(operation.path("responses").has(code));
            }
            for (String code : List.of("401", "403", "500")) {
                assertEquals(
                        "#/components/schemas/AdminEditErrorResponse",
                        operation
                                .path("responses")
                                .path(code)
                                .at("/content/application~1json/schema/$ref")
                                .asText());
            }
            String description = operation.path("description").asText();
            for (String rule :
                    List.of("Read-only", "COMPLETED", "JWT sub", "HALF_UP", "snapshot")) {
                assertTrue(description.contains(rule), rule);
            }
            if (suffix.isEmpty()) {
                assertTrue(description.contains("one point per correct answer"));
                assertTrue(description.contains("weighted"));
            } else {
                for (String rule :
                        List.of(
                                "UNKNOWN",
                                "UNASSIGNED",
                                "NOT additive",
                                "CUSTOM",
                                "EXAMPLE",
                                "ascending")) {
                    assertTrue(description.contains(rule), rule);
                }
            }
        }
    }

    @Test
    void mergedSchemasAndExamplesMatchActualDtosAndZeroData() throws Exception {
        JsonNode document = document();
        JsonNode overall =
                document.path("paths")
                        .path("/api/v1/kanji-quiz/progress")
                        .at("/get/responses/200/content/application~1json");
        QuizProgressResponse success =
                mapper.treeToValue(
                        overall.at("/examples/success/value/data"), QuizProgressResponse.class);
        assertEquals(2, success.completedSessions());
        assertEquals(6, success.answeredCount());
        assertEquals(new BigDecimal("66.67"), success.accuracyPercentage());
        assertEquals(3, success.bestScore());
        assertNotNull(success.latestCompletedAt());
        QuizProgressResponse empty =
                mapper.treeToValue(
                        overall.at("/examples/empty/value/data"), QuizProgressResponse.class);
        assertEquals(0, empty.completedSessions());
        assertEquals(0, empty.accuracyPercentage().signum());
        assertNull(empty.latestCompletedAt());
        JsonNode breakdown =
                document.path("paths")
                        .path("/api/v1/kanji-quiz/progress/breakdown")
                        .at("/get/responses/200/content/application~1json");
        QuizProgressBreakdownResponse buckets =
                mapper.treeToValue(
                        breakdown.at("/examples/success/value/data"),
                        QuizProgressBreakdownResponse.class);
        assertEquals(Classification.ASSIGNED, buckets.levels().get(0).classification());
        assertEquals(Classification.UNKNOWN, buckets.levels().get(1).classification());
        assertEquals(Classification.UNASSIGNED, buckets.lessons().get(0).classification());
        assertNull(buckets.lessons().get(0).lessonId());
        QuizProgressBreakdownResponse none =
                mapper.treeToValue(
                        breakdown.at("/examples/empty/value/data"),
                        QuizProgressBreakdownResponse.class);
        assertTrue(none.levels().isEmpty());
        assertTrue(none.lessons().isEmpty());
        JsonNode schemas = document.at("/components/schemas");
        assertEquals(
                fields(mapper.valueToTree(success)),
                fields(schemas.at("/QuizProgressResponse/properties")));
        assertEquals(
                fields(mapper.valueToTree(buckets)),
                fields(schemas.at("/QuizProgressBreakdownResponse/properties")));
        assertEquals(
                fields(mapper.valueToTree(buckets.levels().get(0))),
                fields(schemas.at("/QuizProgressBucket/properties")));
        assertEquals(
                "#/components/schemas/QuizProgressBucket",
                schemas.at("/QuizProgressBreakdownResponse/properties/levels/items/$ref").asText());
        assertEquals(
                "#/components/schemas/QuizProgressBucket",
                schemas.at("/QuizProgressBreakdownResponse/properties/lessons/items/$ref")
                        .asText());
        assertTrue(
                schemas.at("/QuizProgressResponse/properties/latestCompletedAt/anyOf")
                        .toString()
                        .contains("null"));
        assertEquals(
                "#/components/schemas/QuizProgressResponse",
                overall.at("/schema/allOf/1/properties/data/$ref").asText());
        assertEquals(
                "#/components/schemas/QuizProgressBreakdownResponse",
                breakdown.at("/schema/allOf/1/properties/data/$ref").asText());
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
