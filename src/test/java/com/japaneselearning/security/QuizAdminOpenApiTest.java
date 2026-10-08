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
    void allNineOperationsExposeAdminSecurityTypedResponsesAndErrors() {
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
                for (String status : List.of("200", "400", "401", "403", "404", "409", "500")) {
                    assertFalse(operation.path("responses").path(status).isMissingNode(), status);
                }
                assertTrue(operation.at("/responses/200/content/application~1json/examples/success/$ref")
                        .asText().startsWith("#/components/examples/QuizQuestion"));
                JsonNode success = operation.at(
                        "/responses/200/content/application~1json/schema/allOf/1/properties/data"
                );
                String response = entry.getKey().equals("get") && path.getKey().equals(BASE)
                        ? "QuizQuestionListResponse" : "QuizQuestionResponse";
                assertEquals("#/components/schemas/" + response, success.path("$ref").asText());
                if (!entry.getKey().equals("get")) {
                    assertTrue(operation.at("/requestBody/required").asBoolean());
                    assertFalse(operation.at("/requestBody/content/application~1json/examples").isEmpty());
                }
            }
        }
        assertEquals(9, count);
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
