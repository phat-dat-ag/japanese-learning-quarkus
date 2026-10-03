package com.japaneselearning.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.japaneselearning.flashcard.resource.FlashcardResource;
import com.japaneselearning.vocabulary.admin.resource.*;
import com.japaneselearning.vocabulary.importer.VocabularyImportValidator;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.resource.JlptLevelResource;
import com.japaneselearning.vocabulary.resource.LessonResource;
import com.japaneselearning.vocabulary.resource.VocabularyResource;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class OpenApiResourceContractTest {
    RsaJsonWebKey signingKey;
    private JsonNode document;
    private JsonNode source;
    private final ObjectMapper mapper = new ObjectMapper();

    @BeforeEach
    void loadRuntimeContract() throws Exception {
        String json = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().asString();
        document = mapper.readTree(json);
        try (var input = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            source = new ObjectMapper(new YAMLFactory()).readTree(input);
        }
        Files.writeString(java.nio.file.Path.of("target", "openapi-runtime.json"), json);
    }

    @Test
    void everyResourceOperationHasItsOwnGroupAndCompleteContract() {
        List<Class<?>> resources = List.of(FlashcardResource.class, JlptLevelResource.class,
                LessonResource.class, VocabularyResource.class, AdminVocabularyCoreResource.class,
                AdminVocabularyReadingResource.class, AdminVocabularyMeaningResource.class,
                AdminVocabularyPitchAccentResource.class, AdminVocabularyExampleResource.class,
                AdminVocabularyAssignmentResource.class, AdminVocabularyKanjiResource.class);
        Set<String> resourceTags = new HashSet<>();
        Set<String> operations = new HashSet<>();
        Set<String> operationIds = new HashSet<>();
        for (Class<?> resource : resources) {
            String tag = resource.getAnnotation(Tag.class).name();
            assertTrue(resourceTags.add(tag), "Resources must not share a tag");
            for (var method : resource.getDeclaredMethods()) {
                String verb = method.isAnnotationPresent(GET.class) ? "get"
                        : method.isAnnotationPresent(POST.class) ? "post"
                        : method.isAnnotationPresent(PUT.class) ? "put" : null;
                if (verb == null) {
                    continue;
                }
                String path = resource.getAnnotation(Path.class).value()
                        + (method.isAnnotationPresent(Path.class) ? method.getAnnotation(Path.class).value() : "");
                String label = verb + " " + path;
                operations.add(label);
                JsonNode operation = document.path("paths").path(path).path(verb);
                assertEquals(List.of(tag), mapper.convertValue(operation.path("tags"), List.class), label);
                assertEquals(method.getName(), operation.path("operationId").asText(), label);
                assertTrue(operationIds.add(operation.path("operationId").asText()), label);
                assertFalse(operation.path("summary").asText().isBlank(), label);
                assertFalse(operation.path("description").asText().isBlank(), label);
                assertEquals(source.path("paths").path(path).path(verb).path("description"),
                        operation.path("description"), "Annotations must not overwrite the static description: " + label);
                assertTrue(operation.path("description").asText().contains("Admin"), label);
                JsonNode security = operation.has("security") ? operation.path("security") : document.path("security");
                assertEquals(mapper.valueToTree(List.of(Map.of("bearerAuth", List.of()))), security, label);
                for (String status : List.of("200", "401", "403", "500")) {
                    JsonNode response = resolve(operation.path("responses").path(status));
                    assertFalse(response.path("description").asText().isBlank(), label + " " + status);
                    assertFalse(response.at("/content/application~1json/schema").isMissingNode(), label + " " + status);
                }
                for (JsonNode parameter : operation.path("parameters")) {
                    JsonNode resolved = resolve(parameter);
                    assertFalse(resolved.path("description").asText().isBlank(), label);
                    assertFalse(resolved.path("schema").isMissingNode(), label);
                }
                if (!verb.equals("get")) {
                    assertTrue(operation.at("/requestBody/required").asBoolean(), label);
                    assertEquals(1, operation.at("/requestBody/content").size(), label);
                }
            }
        }
        Set<String> declaredTags = new HashSet<>();
        for (JsonNode tag : document.path("tags")) {
            assertTrue(declaredTags.add(tag.path("name").asText()));
            assertFalse(tag.path("description").asText().isBlank());
        }
        assertEquals(resourceTags, declaredTags);
        Set<String> documented = new HashSet<>();
        document.path("paths").fields().forEachRemaining(path -> path.getValue().fieldNames()
                .forEachRemaining(verb -> {
                    if (Set.of("get", "post", "put", "delete", "patch").contains(verb)) {
                        documented.add(verb + " " + path.getKey());
                    }
                }));
        assertEquals(27, operations.size());
        assertEquals(operations, documented);
    }

    @Test
    void learnerCollectionsExposeRealDtoShapesAndQueryRules() {
        assertData("/api/v1/jlpt-levels", "get", "JlptLevelResponse", true);
        assertData("/api/v1/lessons", "get", "LessonResponse", true);
        assertData("/api/v1/flashcards", "get", "FlashcardListResponse", false);
        assertData("/api/vocabularies", "post", "ImportResult", false);
        assertData("/api/vocabularies/import", "post", "ImportResult", false);
        JsonNode list = schema("FlashcardListResponse");
        assertEquals(Set.of("flashcardItems", "page", "size", "totalElements", "totalPages"), fields(list.path("properties")));
        assertEquals("#/components/schemas/FlashcardListItemResponse", list.at("/properties/flashcardItems/items/$ref").asText());
        JsonNode flashcards = document.path("paths").path("/api/v1/flashcards").path("get");
        assertEquals("N5", parameter(flashcards, "level").at("/schema/default").asText());
        assertEquals(0, parameter(flashcards, "page").at("/schema/default").asInt());
        assertEquals(20, parameter(flashcards, "size").at("/schema/default").asInt());
        assertEquals(100, parameter(flashcards, "size").at("/schema/maximum").asInt());
        assertEquals(1, parameter(flashcards, "lesson").at("/schema/minimum").asInt());
        assertFalse(parameter(flashcards, "level").path("required").asBoolean());
        assertTrue(parameter(document.path("paths").path("/api/v1/lessons").path("get"), "level")
                .path("required").asBoolean());
        for (var nullable : Map.of("FlashcardLevelResponse", List.of("displayOrder"),
                "FlashcardLessonResponse", List.of("description"),
                "FlashcardKanjiResponse", List.of("strokeCount", "meaningVi", "meaningEn")).entrySet()) {
            for (String field : nullable.getValue()) {
                assertFalse(strings(schema(nullable.getKey()).path("required")).contains(field), field);
            }
        }
    }

    @Test
    void bothFileEndpointsHaveBinaryControlsAndCorrectMediaTypeErrors() {
        for (String path : List.of("/api/v1/lessons/batch", "/api/vocabularies/import")) {
            JsonNode operation = document.path("paths").path(path).path("post");
            JsonNode content = operation.at("/requestBody/content");
            assertEquals(Set.of("multipart/form-data"), fields(content));
            JsonNode body = content.path("multipart/form-data").path("schema");
            assertEquals("object", body.path("type").asText());
            assertEquals(Set.of("file"), strings(body.path("required")));
            assertEquals("string", body.at("/properties/file/type").asText());
            assertEquals("binary", body.at("/properties/file/format").asText());
            assertTrue(body.at("/properties/file/description").asText().contains("Example file content"));
            assertFalse(resolve(operation.path("responses").path("415")).path("description").asText()
                    .contains("must be application/json"));
        }
    }

    @Test
    void importSchemaAndExampleMatchActualDtoValidation() throws Exception {
        JsonNode request = document.path("paths").path("/api/vocabularies").path("post")
                .at("/requestBody/content/application~1json/schema");
        assertEquals("array", request.path("type").asText());
        assertEquals(1, request.path("minItems").asInt());
        assertFalse(request.has("maxItems"), "The configured limit must not be presented as a fixed limit");
        assertEquals("#/components/schemas/VocabularyImportItem", request.at("/items/$ref").asText());
        assertEquals(Set.of("word", "normalizedWord", "levels", "lessons", "readings", "meanings", "partsOfSpeech", "examples"),
                strings(schema("VocabularyImportItem").path("required")));
        for (Class<?> dto : List.of(VocabularyImportItem.class,
                com.japaneselearning.vocabulary.importer.dto.LessonImportItem.class,
                com.japaneselearning.vocabulary.importer.dto.VocabularyReadingImportItem.class,
                com.japaneselearning.vocabulary.importer.dto.VocabularyMeaningImportItem.class,
                com.japaneselearning.vocabulary.importer.dto.VocabularyPitchAccentImportItem.class,
                com.japaneselearning.vocabulary.importer.dto.VocabularyKanjiImportItem.class,
                com.japaneselearning.vocabulary.importer.dto.KanjiReadingImportItem.class,
                com.japaneselearning.vocabulary.importer.dto.VocabularyExampleImportItem.class)) {
            Set<String> expected = new HashSet<>();
            for (var field : dto.getFields()) {
                expected.add(field.getName());
            }
            assertEquals(expected, fields(schema(dto.getSimpleName()).path("properties")), dto.getSimpleName());
        }
        List<VocabularyImportItem> items = mapper.convertValue(document.at("/components/examples/VocabularyImport/value"),
                mapper.getTypeFactory().constructCollectionType(List.class, VocabularyImportItem.class));
        new VocabularyImportValidator().validate(items);
    }

    @Test
    void malformedParametersHaveDocumentedErrorResponses() throws Exception {
        String token = new JwtTestTokens(signingKey).token("Admin");
        for (var endpoint : Map.of("/api/v1/flashcards?size=invalid", "/api/v1/flashcards",
                "/api/v1/flashcards/invalid", "/api/v1/flashcards/{id}",
                "/api/v1/lessons", "/api/v1/lessons").entrySet()) {
            var response = given().auth().oauth2(token).get(endpoint.getKey());
            assertTrue(response.statusCode() >= 400 && response.statusCode() < 500);
            JsonNode error = resolve(document.path("paths").path(endpoint.getValue()).path("get")
                    .path("responses").path(Integer.toString(response.statusCode())));
            assertFalse(error.at("/content/application~1json/schema").isMissingNode(), endpoint.getKey()
                    + " must document HTTP " + response.statusCode());
            assertFalse(response.jsonPath().getBoolean("success"));
        }
    }

    private void assertData(String path, String verb, String dto, boolean array) {
        JsonNode success = document.path("paths").path(path).path(verb)
                .at("/responses/200/content/application~1json/schema");
        JsonNode data = success.at("/allOf/1/properties/data");
        if (array) {
            assertEquals("array", data.path("type").asText());
            data = data.path("items");
        }
        assertEquals("#/components/schemas/" + dto, data.path("$ref").asText());
    }

    private JsonNode schema(String name) {
        return document.path("components").path("schemas").path(name);
    }

    private JsonNode resolve(JsonNode node) {
        return node.has("$ref") ? document.at(node.path("$ref").asText().substring(1)) : node;
    }

    private JsonNode parameter(JsonNode operation, String name) {
        for (JsonNode candidate : operation.path("parameters")) {
            JsonNode parameter = resolve(candidate);
            if (parameter.path("name").asText().equals(name)) {
                return parameter;
            }
        }
        fail("Missing parameter: " + name);
        return null;
    }

    private Set<String> fields(JsonNode node) {
        Set<String> names = new HashSet<>();
        node.fieldNames().forEachRemaining(names::add);
        return names;
    }

    private Set<String> strings(JsonNode node) {
        Set<String> values = new HashSet<>();
        node.forEach(value -> values.add(value.asText()));
        return values;
    }
}
