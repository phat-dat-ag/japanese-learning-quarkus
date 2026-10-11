package com.japaneselearning.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.quiz.admin.dto.QuizQuestionCreateRequest;
import com.japaneselearning.quiz.domain.QuestionOption;
import com.japaneselearning.quiz.domain.QuestionTarget;
import com.japaneselearning.quiz.domain.QuizQuestionRules;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class QuizAdminOpenApiTest {
    private static final String BASE = "/api/v1/admin/kanji-quiz/questions";
    private final ObjectMapper mapper = new ObjectMapper();
    private JsonNode document;

    @BeforeEach
    void loadMergedDocument() throws Exception {
        document = mapper.readTree(given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().asString());
    }

    @Test
    void allTenOperationsExposeAdminSecurityTypedResponsesAndErrors() {
        int count = 0;
        var paths = document.path("paths").fields();
        while (paths.hasNext()) {
            var path = paths.next();
            if (!path.getKey().startsWith(BASE)) {
                continue;
            }
            var operations = path.getValue().fields();
            while (operations.hasNext()) {
                var entry = operations.next();
                if (!List.of("get", "put", "post").contains(entry.getKey())) {
                    continue;
                }
                count++;
                JsonNode operation = entry.getValue();
                assertEquals("Admin Kanji Quiz", operation.path("tags").get(0).asText());
                assertTrue(operation.path("description").asText().contains("Admin"));
                assertTrue(operation.path("security").get(0).has("bearerAuth"));
                boolean importing = path.getKey().equals(BASE + "/import");
                List<String> statuses = importing
                        ? List.of("200", "400", "401", "403", "413", "415", "500")
                        : List.of("200", "400", "401", "403", "404", "409", "500");
                for (String status : statuses) {
                    assertFalse(operation.path("responses").path(status).isMissingNode(), status);
                }
                assertTrue(operation.at("/responses/200/content/application~1json/examples/success/$ref")
                        .asText().startsWith("#/components/examples/QuizQuestion"));
                JsonNode success = operation.at(
                        "/responses/200/content/application~1json/schema/allOf/1/properties/data"
                );
                String response = entry.getKey().equals("get") && path.getKey().equals(BASE)
                        ? "QuizQuestionListResponse" : "QuizQuestionResponse";
                if (importing) {
                    response = "QuizImportResponse";
                }
                assertEquals("#/components/schemas/" + response, success.path("$ref").asText());
                if (!entry.getKey().equals("get")) {
                    assertTrue(operation.at("/requestBody/required").asBoolean());
                    String mediaType = importing ? "multipart~1form-data" : "application~1json";
                    assertFalse(operation.at("/requestBody/content/" + mediaType + "/examples").isEmpty());
                }
            }
        }
        assertEquals(10, count);
    }

    @Test
    void createExamplesDeserializeAndSatisfyRealUnicodeAndOptionRules() throws Exception {
        JsonNode examples = document.path("paths").path(BASE).path("post")
                .at("/requestBody/content/application~1json/examples");
        for (String name : List.of("custom", "example")) {
            var request = mapper.treeToValue(examples.path(name).path("value"), QuizQuestionCreateRequest.class);
            QuizQuestionRules.validateOptions(request.options().stream()
                    .map(option -> new QuestionOption(option.text(), option.correct())).toList());
            var content = request.content();
            var target = new QuestionTarget(content.targetStart(), content.targetLength(), content.targetReading());
            if (name.equals("custom")) {
                target.validateAgainst(content.sentenceReading());
            } else {
                assertTrue(content.exampleId() > 0);
                assertNull(content.sentenceReading());
            }
        }
    }

    @Test
    void importContractExposesBinaryFileTypedArrayExamplesAndConfigurableLimits() throws Exception {
        JsonNode operation = document.path("paths").path(BASE + "/import").path("post");
        JsonNode multipart = operation.at("/requestBody/content/multipart~1form-data");
        JsonNode file = multipart.at("/schema/properties/file");
        assertEquals("binary", file.path("format").asText());
        assertEquals("application/json", file.path("contentMediaType").asText());
        assertEquals("#/components/schemas/QuizQuestionImportArray", file.at("/contentSchema/$ref").asText());
        JsonNode array = document.at("/components/schemas/QuizQuestionImportArray");
        assertEquals("#/components/schemas/QuizQuestionCreateRequest", array.at("/items/$ref").asText());
        assertFalse(array.has("maxItems"), "Deployment limit is configurable");
        assertTrue(operation.path("description").asText().contains("quiz.import.max-file-bytes"));
        assertTrue(operation.path("description").asText().contains("quiz.import.max-items"));
        for (String name : List.of("custom", "example", "mixed")) {
            JsonNode items = mapper.readTree(multipart.path("examples").path(name).at("/value/file").asText());
            assertTrue(items.isArray());
            assertEquals(name.equals("mixed") ? 2 : 1, items.size());
            for (JsonNode item : items) {
                var request = mapper.treeToValue(item, QuizQuestionCreateRequest.class);
                QuizQuestionRules.validateOptions(request.options().stream()
                        .map(option -> new QuestionOption(option.text(), option.correct())).toList());
                var content = request.content();
                var target = new QuestionTarget(content.targetStart(), content.targetLength(), content.targetReading());
                if (content.sentenceReading() != null) {
                    target.validateAgainst(content.sentenceReading());
                } else {
                    assertTrue(content.exampleId() > 0);
                }
            }
        }
    }

    @Test
    void importResponseDocumentsOrderedPartialSuccessAndItemErrors() {
        JsonNode schemas = document.at("/components/schemas");
        JsonNode response = schemas.path("QuizImportResponse");
        for (String field : List.of("total", "imported", "failed", "results")) {
            assertTrue(response.path("required").toString().contains(field));
        }
        assertFalse(response.path("properties").has("questions"));
        assertEquals(0, response.at("/properties/imported/minimum").asInt());
        assertEquals("#/components/schemas/QuizImportResult", response.at("/properties/results/items/$ref").asText());
        JsonNode result = schemas.path("QuizImportResult");
        assertEquals(2, result.path("oneOf").size());
        assertEquals("#/components/schemas/QuizImportError", result.at("/properties/errors/items/$ref").asText());
        for (String field : List.of("field", "code", "message")) {
            assertTrue(schemas.path("QuizImportError").path("required").toString().contains(field));
        }
        for (String name : List.of("Success", "AllSuccess", "AllFailed")) {
            JsonNode data = document.at("/components/examples/QuizQuestionImport" + name + "/value/data");
            assertEquals(data.path("total").asInt(), data.path("imported").asInt() + data.path("failed").asInt());
            assertEquals(data.path("total").asInt(), data.path("results").size());
            int index = 0;
            for (JsonNode item : data.path("results")) {
                assertEquals(index++, item.path("index").asInt());
                if (item.path("status").asText().equals("IMPORTED")) {
                    assertTrue(item.has("questionId"));
                    assertTrue(item.has("version"));
                    assertFalse(item.has("errors"));
                } else {
                    assertEquals("FAILED", item.path("status").asText());
                    assertFalse(item.has("questionId"));
                    assertFalse(item.path("errors").isEmpty());
                }
            }
        }
    }

    @Test
    void schemasExposeVersionOwnershipAndNoOptionOrder() {
        JsonNode schemas = document.at("/components/schemas");
        for (String name : List.of("QuizQuestionUpdateRequest", "QuizOptionTextRequest",
                "QuizCorrectOptionRequest", "QuizVersionRequest")) {
            assertTrue(schemas.path(name).path("required").toString().contains("version"));
        }
        assertFalse(schemas.path("QuizOptionResponse").path("properties").has("displayOrder"));
        assertTrue(schemas.path("QuizOptionResponse").path("properties").has("correct"));
        assertEquals(4, schemas.path("QuizQuestionCreateRequest").at("/properties/options/minItems").asInt());
        assertEquals(4, schemas.path("QuizQuestionCreateRequest").at("/properties/options/maxItems").asInt());
        assertTrue(schemas.path("QuizQuestionResponse").path("properties").has("sourceInvalidated"));
    }
}
