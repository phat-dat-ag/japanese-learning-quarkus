package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.quiz.player.dto.QuizNextQuestionResponse;
import com.japaneselearning.quiz.player.dto.QuizSessionCreateRequest;
import com.japaneselearning.quiz.player.dto.QuizSessionResponse;

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
class QuizGameOpenApiTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void mergedContractExposesOnlyEightPlayerOperationsAndSafeResponses() throws Exception {
        JsonNode document =
                mapper.readTree(
                        given().accept("application/json")
                                .get("/q/openapi")
                                .then()
                                .statusCode(200)
                                .extract()
                                .asString());
        Set<String> paths = new HashSet<>();
        document.path("paths")
                .fieldNames()
                .forEachRemaining(
                        path -> {
                            if (path.startsWith("/api/v1/kanji-quiz")) {
                                paths.add(path);
                            }
                        });
        assertEquals(
                Set.of(
                        "/api/v1/kanji-quiz/config",
                        "/api/v1/kanji-quiz/sessions",
                        "/api/v1/kanji-quiz/sessions/{id}",
                        "/api/v1/kanji-quiz/sessions/{id}/next",
                        "/api/v1/kanji-quiz/sessions/{id}/answers",
                        "/api/v1/kanji-quiz/sessions/{id}/finish",
                        "/api/v1/kanji-quiz/history",
                        "/api/v1/kanji-quiz/history/{sessionId}"),
                paths);
        for (String path : paths) {
            JsonNode operation =
                    document.path("paths")
                            .path(path)
                            .path(
                                    path.equals("/api/v1/kanji-quiz/sessions")
                                            || path.endsWith("/answers")
                                            || path.endsWith("/finish")
                                            ? "post"
                                            : "get");
            assertEquals("Kanji Quiz", operation.path("tags").get(0).asText());
            assertTrue(operation.path("description").asText().contains("User or Admin"));
            assertTrue(operation.path("security").get(0).has("bearerAuth"));
            for (String status : List.of("200", "401", "403", "500")) {
                assertTrue(operation.path("responses").has(status));
            }
        }
        JsonNode properties =
                document.at("/components/schemas/QuizSessionCreatedResponse/properties");
        Set<String> fields = new HashSet<>();
        properties.fieldNames().forEachRemaining(fields::add);
        assertEquals(Set.of("sessionId", "status", "questionCount", "createdAt"), fields);
        JsonNode request = document.at("/components/schemas/QuizSessionCreateRequest");
        assertEquals(1, request.at("/properties/questionCount/minimum").asInt());
        assertEquals(100, request.at("/properties/questionCount/maximum").asInt());
        assertFalse(request.path("properties").has("userId"));
        JsonNode operation =
                document.path("paths").path("/api/v1/kanji-quiz/sessions").path("post");
        assertTrue(operation.path("responses").has("400"));
        assertTrue(operation.path("responses").has("409"));
        JsonNode examples = operation.at("/requestBody/content/application~1json/examples");
        for (String name : List.of("filtered", "unfiltered")) {
            var value =
                    mapper.treeToValue(
                            examples.path(name).path("value"), QuizSessionCreateRequest.class);
            assertTrue(value.questionCount() >= 1 && value.questionCount() <= 100);
        }
    }

    @Test
    void retrievalContractsDocumentOwnershipNullNextAndOnlySafeFields() throws Exception {
        JsonNode document =
                mapper.readTree(
                        given().accept("application/json")
                                .get("/q/openapi")
                                .then()
                                .statusCode(200)
                                .extract()
                                .asString());
        JsonNode schemas = document.path("components").path("schemas");
        assertEquals(
                Set.of("sessionId", "status", "questionCount", "answeredCount", "score"),
                fields(schemas.path("QuizSessionResponse").path("properties")));
        assertEquals(
                Set.of("sessionId", "status", "question"),
                fields(schemas.path("QuizNextQuestionResponse").path("properties")));
        assertEquals(
                Set.of(
                        "sessionQuestionId",
                        "questionNumber",
                        "sentenceReading",
                        "targetStart",
                        "targetLength",
                        "options"),
                fields(schemas.path("QuizPlayerQuestion").path("properties")));
        assertEquals(
                Set.of("id", "text"), fields(schemas.path("QuizPlayerOption").path("properties")));
        JsonNode options = schemas.at("/QuizPlayerQuestion/properties/options");
        assertEquals(4, options.path("minItems").asInt());
        assertEquals(4, options.path("maxItems").asInt());
        assertEquals("#/components/schemas/QuizPlayerOption", options.at("/items/$ref").asText());
        assertTrue(
                schemas.at("/QuizNextQuestionResponse/properties/question/anyOf")
                        .toString()
                        .contains("\"null\""));

        for (String suffix : List.of("", "/next")) {
            JsonNode operation =
                    document.path("paths")
                            .path("/api/v1/kanji-quiz/sessions/{id}" + suffix)
                            .path("get");
            for (String status : List.of("200", "400", "401", "403", "404", "500")) {
                assertTrue(operation.path("responses").has(status), status);
            }
            assertTrue(operation.path("description").asText().contains("Admin"));
            assertTrue(
                    operation
                            .path("responses")
                            .path("404")
                            .path("description")
                            .asText()
                            .contains("owned by another"));
            JsonNode parameter = operation.path("parameters").get(0);
            assertEquals("id", parameter.path("name").asText());
            assertEquals("path", parameter.path("in").asText());
            assertTrue(parameter.path("required").asBoolean());
            assertEquals(1, parameter.at("/schema/minimum").asInt());
            assertFalse(operation.has("requestBody"));
            JsonNode examples = operation.at("/responses/200/content/application~1json/examples");
            JsonNode data = examples.at("/success/value/data");
            if (suffix.isEmpty()) {
                var summary = mapper.treeToValue(data, QuizSessionResponse.class);
                assertEquals(3, summary.answeredCount());
                assertEquals(2, summary.score());
            } else {
                var next = mapper.treeToValue(data, QuizNextQuestionResponse.class);
                assertEquals(4, next.question().options().size());
                assertEquals(0, next.question().questionNumber());
                var exhausted =
                        mapper.treeToValue(
                                examples.at("/noNext/value/data"), QuizNextQuestionResponse.class);
                org.junit.jupiter.api.Assertions.assertNull(exhausted.question());
            }
        }
    }

    @Test
    void answerContractDocumentsSnapshotInputFeedbackAndFailures() throws Exception {
        JsonNode document =
                mapper.readTree(
                        given().accept("application/json")
                                .get("/q/openapi")
                                .then()
                                .statusCode(200)
                                .extract()
                                .asString());
        JsonNode operation =
                document.path("paths")
                        .path("/api/v1/kanji-quiz/sessions/{id}/answers")
                        .path("post");
        for (String status : List.of("200", "400", "401", "403", "404", "409", "415", "500")) {
            assertTrue(operation.path("responses").has(status), status);
        }
        assertTrue(operation.at("/requestBody/required").asBoolean());
        JsonNode schemas = document.at("/components/schemas");
        JsonNode request = schemas.path("QuizAnswerRequest");
        assertEquals(
                Set.of("sessionQuestionId", "selectedOptionId"),
                fields(request.path("properties")));
        for (String field : List.of("sessionQuestionId", "selectedOptionId")) {
            assertEquals(1, request.path("properties").path(field).path("minimum").asInt());
            assertTrue(request.path("required").toString().contains(field));
        }
        var example =
                mapper.treeToValue(
                        operation.at(
                                "/requestBody/content/application~1json/examples/answer/value"),
                        com.japaneselearning.quiz.player.dto.QuizAnswerRequest.class);
        assertEquals(101L, example.sessionQuestionId());
        assertEquals(402L, example.selectedOptionId());
        assertEquals(
                Set.of(
                        "sessionQuestionId",
                        "correct",
                        "correctOptionId",
                        "explanationVi",
                        "explanationEn",
                        "score",
                        "answeredCount",
                        "remainingCount"),
                fields(schemas.at("/QuizAnswerResponse/properties")));
        for (String name : List.of("correct", "incorrect")) {
            var feedback =
                    mapper.treeToValue(
                            operation.at(
                                    "/responses/200/content/application~1json/examples/"
                                            + name
                                            + "/value/data"),
                            com.japaneselearning.quiz.player.dto.QuizAnswerResponse.class);
            assertEquals(name.equals("correct"), feedback.correct());
            assertEquals(1, feedback.answeredCount());
            assertEquals(9, feedback.remainingCount());
        }
    }

    @Test
    void completionContractHasNoBodyAndDocumentsFinalCountsAndErrors() throws Exception {
        JsonNode document =
                mapper.readTree(
                        given().accept("application/json")
                                .get("/q/openapi")
                                .then()
                                .statusCode(200)
                                .extract()
                                .asString());
        JsonNode operation =
                document.path("paths").path("/api/v1/kanji-quiz/sessions/{id}/finish").path("post");
        assertFalse(operation.has("requestBody"));
        assertTrue(operation.path("security").get(0).has("bearerAuth"));
        for (String code : List.of("200", "400", "401", "403", "404", "409", "500")) {
            assertTrue(operation.path("responses").has(code), code);
        }
        JsonNode properties =
                document.at("/components/schemas/QuizSessionCompletionResponse/properties");
        assertEquals(
                Set.of(
                        "sessionId",
                        "status",
                        "questionCount",
                        "correctCount",
                        "incorrectCount",
                        "score",
                        "completedAt"),
                fields(properties));
        assertEquals("COMPLETED", properties.at("/status/enum/0").asText());
        JsonNode data =
                operation.at(
                        "/responses/200/content/application~1json/examples/completed/value/data");
        var completed =
                new ObjectMapper()
                        .findAndRegisterModules()
                        .treeToValue(
                                data,
                                com.japaneselearning.quiz.player.dto.QuizSessionCompletionResponse
                                        .class);
        assertEquals(
                com.japaneselearning.quiz.domain.QuizSessionStatus.COMPLETED, completed.status());
        assertEquals(
                completed.questionCount(), completed.correctCount() + completed.incorrectCount());
        assertEquals(completed.correctCount(), completed.score());
        assertEquals(
                java.time.LocalDateTime.parse("2026-10-09T10:15:30.123456"),
                completed.completedAt());
        assertTrue(
                operation.at("/responses/404/description").asText().contains("owned by another"));
        assertTrue(
                operation
                        .at("/responses/409/description")
                        .asText()
                        .contains("QUIZ_FINISH_CONFLICT"));
    }

    private Set<String> fields(JsonNode properties) {
        Set<String> fields = new HashSet<>();
        properties.fieldNames().forEachRemaining(fields::add);
        return fields;
    }
}
