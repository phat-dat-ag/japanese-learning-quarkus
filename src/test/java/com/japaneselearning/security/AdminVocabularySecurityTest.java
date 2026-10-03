package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.japaneselearning.vocabulary.admin.resource.AdminVocabularyAssignmentResource;
import com.japaneselearning.vocabulary.admin.resource.AdminVocabularyCoreResource;
import com.japaneselearning.vocabulary.admin.resource.AdminVocabularyExampleResource;
import com.japaneselearning.vocabulary.admin.resource.AdminVocabularyKanjiResource;
import com.japaneselearning.vocabulary.admin.resource.AdminVocabularyMeaningResource;
import com.japaneselearning.vocabulary.admin.resource.AdminVocabularyPitchAccentResource;
import com.japaneselearning.vocabulary.admin.resource.AdminVocabularyReadingResource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import org.junit.jupiter.api.Test;

import java.lang.reflect.ParameterizedType;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;

import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class AdminVocabularySecurityTest {
    RsaJsonWebKey signingKey;

    @ParameterizedTest
    @CsvSource({
            "PUT,''",
            "POST,/readings",
            "PUT,/readings/1",
            "POST,/meanings",
            "PUT,/meanings/1",
            "POST,/examples",
            "PUT,/examples/1",
            "POST,/pitch-accents",
            "PUT,/pitch-accents/1",
            "POST,/levels",
            "PUT,/levels/1",
            "POST,/lessons",
            "PUT,/lessons/1",
            "POST,/parts-of-speech",
            "POST,/kanji",
            "PUT,/kanji/1",
            "POST,/kanji/1/readings",
            "PUT,/kanji/1/readings/1"
    })
    void allEditOperationsRequireAdmin(String method, String suffix) throws Exception {
        String path = "/api/v1/admin/vocabularies/1" + suffix;
        given().contentType("application/json")
                .body("{}")
                .request(method, path)
                .then()
                .statusCode(401)
                .body("success", equalTo(false), "meta.traceId", notNullValue());
        given().auth()
                .oauth2(new JwtTestTokens(signingKey).token("User"))
                .contentType("application/json")
                .body("{}")
                .request(method, path)
                .then()
                .statusCode(403)
                .body("success", equalTo(false), "meta.traceId", notNullValue());
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "readings",
                    "meanings",
                    "examples",
                    "pitch-accents",
                    "levels",
                    "lessons",
                    "parts-of-speech",
                    "kanji",
                    "kanji/1/readings"
            })
    void addRequiresANonemptyArrayOfValidItems(String section) throws Exception {
        String token = new JwtTestTokens(signingKey).token("Admin");
        for (String body : new String[]{"{}", "[]", "null", "[null]", "[{}]"}) {
            given().auth()
                    .oauth2(token)
                    .contentType("application/json")
                    .body(body)
                    .post("/api/v1/admin/vocabularies/1/" + section)
                    .then()
                    .statusCode(400);
        }
    }

    @ParameterizedTest
    @CsvSource({
            "PUT,'',VocabularyCoreResult,vocabularyId",
            "POST,/readings,VocabularyReadingResult,readingId",
            "PUT,/readings/{readingId},VocabularyReadingResult,readingId",
            "POST,/meanings,VocabularyMeaningResult,meaningId",
            "PUT,/meanings/{meaningId},VocabularyMeaningResult,meaningId",
            "POST,/pitch-accents,VocabularyPitchAccentResult,pitchAccentId",
            "PUT,/pitch-accents/{pitchAccentId},VocabularyPitchAccentResult,pitchAccentId",
            "POST,/examples,VocabularyExampleResult,exampleId",
            "PUT,/examples/{exampleId},VocabularyExampleResult,exampleId",
            "POST,/levels,VocabularyLevelResult,levelId",
            "PUT,/levels/{levelId},VocabularyLevelResult,levelId",
            "POST,/lessons,VocabularyLessonResult,lessonId",
            "PUT,/lessons/{lessonId},VocabularyLessonResult,lessonId",
            "POST,/parts-of-speech,VocabularyPartOfSpeechResult,partOfSpeechId",
            "POST,/kanji,VocabularyKanjiResult,kanjiId",
            "PUT,/kanji/{kanjiId},VocabularyKanjiResult,kanjiId",
            "POST,/kanji/{kanjiId}/readings,KanjiReadingResult,kanjiReadingId",
            "PUT,/kanji/{kanjiId}/readings/{readingId},KanjiReadingResult,kanjiReadingId"
    })
    void openApiIdentifiesEachResultAndExposesNoDelete(
            String method, String suffix, String resultSchema, String idField) {
        var document =
                given().accept("application/json")
                        .get("/q/openapi")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath();
        String path = "paths.'/api/v1/admin/vocabularies/{vocabularyId}" + suffix + "'";
        String operation = path + "." + method.toLowerCase(java.util.Locale.ROOT);
        String dataSchema =
                operation
                        + ".responses.'200'.content.'application/json'.schema.allOf[1].properties.data";
        String resultReference =
                method.equals("POST") ? dataSchema + ".items.'$ref'" : dataSchema + ".'$ref'";
        assertEquals("#/components/schemas/" + resultSchema, document.getString(resultReference));
        assertEquals(
                java.util.Set.of(idField),
                document.getMap("components.schemas." + resultSchema + ".properties").keySet());
        assertNull(document.getMap(path + ".delete"));
        for (String status : new String[]{"200", "400", "401", "403", "404"}) {
            assertNotNull(document.getMap(operation + ".responses.'" + status + "'"));
        }
        boolean orderOnly = suffix.equals("/levels/{levelId}") || suffix.equals("/lessons/{lessonId}");
        if (orderOnly) {
            assertNull(document.getMap(operation + ".responses.'409'"));
        } else {
            assertNotNull(document.getMap(operation + ".responses.'409'"));
        }
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "readings",
                    "meanings",
                    "examples",
                    "pitch-accents",
                    "levels",
                    "lessons",
                    "parts-of-speech",
                    "kanji",
                    "kanji/{kanjiId}/readings"
            })
    void openApiDocumentsArrayAndStatusContract(String section) {
        var document =
                given().accept("application/json")
                        .get("/q/openapi")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath();
        String operation = "paths.'/api/v1/admin/vocabularies/{vocabularyId}/" + section + "'.post";
        assertEquals(
                "array",
                document.getString(
                        operation + ".requestBody.content.'application/json'.schema.type"));
        for (String status : new String[]{"200", "400", "401", "403", "404", "409"}) {
            org.junit.jupiter.api.Assertions.assertNotNull(
                    document.getMap(operation + ".responses.'" + status + "'"));
        }
    }

    @Test
    void sourceAndRuntimeContractsDescribeEveryAdminRequest() throws Exception {
        for (JsonNode document : contractDocuments()) {
            assertTrue(document.path("openapi").asText().startsWith("3.1."));
            assertFalse(document.at("/info/title").asText().isBlank());
            assertFalse(document.at("/info/version").asText().isBlank());
            assertEquals("http", document.at("/components/securitySchemes/bearerAuth/type").asText());
            assertEquals("bearer", document.at("/components/securitySchemes/bearerAuth/scheme").asText());
            assertEquals("JWT", document.at("/components/securitySchemes/bearerAuth/bearerFormat").asText());
            assertLocalReferencesResolve(document, document);
            assertOperationMetadataAndPathParameters(document);
            int operationCount = 0;
            for (Class<?> resource : List.of(AdminVocabularyCoreResource.class,
                    AdminVocabularyReadingResource.class, AdminVocabularyMeaningResource.class,
                    AdminVocabularyPitchAccentResource.class, AdminVocabularyExampleResource.class,
                    AdminVocabularyAssignmentResource.class, AdminVocabularyKanjiResource.class)) {
                for (var method : resource.getDeclaredMethods()) {
                    boolean add = method.isAnnotationPresent(POST.class);
                    if (!add && !method.isAnnotationPresent(PUT.class)) {
                        continue;
                    }
                    operationCount++;
                    String path = resource.getAnnotation(Path.class).value()
                            + (method.isAnnotationPresent(Path.class) ? method.getAnnotation(Path.class).value() : "");
                    JsonNode operation = document.path("paths").path(path).path(add ? "post" : "put");
                    assertEquals(method.getName(), operation.path("operationId").asText(), path);
                    assertEquals(Set.of(resource.getAnnotation(
                                    org.eclipse.microprofile.openapi.annotations.tags.Tag.class).name()),
                            strings(operation.path("tags")), path);
                    assertTrue(operation.at("/requestBody/required").asBoolean(), path);
                    JsonNode request = operation.at("/requestBody/content/application~1json/schema");
                    var bodyType = method.getGenericParameterTypes()[method.getParameterCount() - 1];
                    Class<?> dto;
                    if (add) {
                        dto = (Class<?>) ((ParameterizedType) bodyType).getActualTypeArguments()[0];
                        assertEquals("array", request.path("type").asText(), path);
                        assertEquals(1, request.path("minItems").asInt(), path);
                        assertEquals(100, request.path("maxItems").asInt(), path);
                        request = request.path("items");
                    } else {
                        dto = (Class<?>) bodyType;
                    }
                    if (path.endsWith("/lessons/{lessonId}")) {
                        assertEquals(1, request.at("/allOf/1/properties/displayOrder/minimum").asInt());
                        request = request.at("/allOf/0");
                    }
                    assertEquals("#/components/schemas/" + dto.getSimpleName(), request.path("$ref").asText(), path);
                    assertRequestMatchesDto(document, dto);
                    boolean orderOnly = path.endsWith("/levels/{levelId}") || path.endsWith("/lessons/{lessonId}");
                    for (String status : List.of("400", "401", "403", "404", "409", "415", "500")) {
                        if (orderOnly && status.equals("409")) {
                            assertFalse(operation.path("responses").has("409"));
                            continue;
                        }
                        JsonNode response = resolve(document, operation.path("responses").path(status));
                        assertFalse(response.path("description").asText().isBlank(), path + " " + status);
                        assertEquals("#/components/schemas/AdminEditErrorResponse",
                                response.at("/content/application~1json/schema/$ref").asText(), path + " " + status);
                    }
                    JsonNode success = operation.at("/responses/200/content/application~1json/schema");
                    assertEquals("#/components/schemas/AdminEditResponse", success.at("/allOf/0/$ref").asText());
                    assertEquals(Set.of("data"), strings(success.at("/allOf/1/required")));
                    JsonNode data = success.at("/allOf/1/properties/data");
                    if (add) {
                        assertEquals("array", data.path("type").asText());
                        data = data.path("items");
                    }
                    // Existing parameterized tests pin each domain result name and ID.
                    JsonNode result = resolve(document, data);
                    assertEquals(1, result.path("properties").size());
                    assertEquals(strings(result.path("required")), fieldNames(result.path("properties")));
                    result.path("properties").forEach(id -> {
                        assertEquals("integer", id.path("type").asText());
                        assertEquals("int64", id.path("format").asText());
                    });
                }
            }
            assertEquals(18, operationCount);
            assertEnvelopeContract(document);
        }
    }

    private List<JsonNode> contractDocuments() throws Exception {
        var yaml = new ObjectMapper(new YAMLFactory().enable(JsonParser.Feature.STRICT_DUPLICATE_DETECTION));
        JsonNode source;
        try (var input = getClass().getResourceAsStream("/META-INF/openapi.yaml")) {
            assertNotNull(input);
            source = yaml.readTree(input);
        }
        String runtime = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().asString();
        JsonNode generated = new ObjectMapper().readTree(runtime);
        source.path("paths").fields().forEachRemaining(path -> {
            if (path.getKey().startsWith("/api/v1/admin/vocabularies/")) {
                assertEquals(path.getValue(), generated.path("paths").path(path.getKey()),
                        "Runtime must preserve the complete static contract: " + path.getKey());
            }
        });
        return List.of(source, generated);
    }

    private void assertOperationMetadataAndPathParameters(JsonNode document) {
        Set<String> operationIds = new HashSet<>();
        document.path("paths").fields().forEachRemaining(path -> {
            path.getValue().fields().forEachRemaining(entry -> {
                if (!Set.of("get", "post", "put", "patch", "delete", "head", "options", "trace")
                        .contains(entry.getKey())) {
                    return;
                }
                JsonNode operation = entry.getValue();
                String operationId = operation.path("operationId").asText();
                if (!operationId.isBlank()) {
                    assertTrue(operationIds.add(operationId), "Duplicate operationId: " + operationId);
                }
                if (!path.getKey().startsWith("/api/v1/admin/vocabularies/")) {
                    return;
                }
                assertFalse(operationId.isBlank(), path.getKey());
                assertFalse(operation.path("summary").asText().isBlank());
                assertTrue(operation.path("description").asText().contains("Admin"));
                assertEquals(1, operation.path("tags").size());
                assertFalse(strings(operation.path("tags")).contains("Admin Vocabulary"));
                assertEquals(1, operation.path("security").size());
                assertEquals(0, operation.at("/security/0/bearerAuth").size());
                assertTrue(operation.at("/security/0/bearerAuth").isArray());
                assertFalse(path.getValue().has("delete"));
                Set<String> expected = new HashSet<>();
                var matcher = java.util.regex.Pattern.compile("\\{([^}]+)\\}").matcher(path.getKey());
                while (matcher.find()) {
                    expected.add(matcher.group(1));
                }
                Set<String> actual = new HashSet<>();
                for (JsonNode parameter : operation.path("parameters")) {
                    JsonNode resolved = resolve(document, parameter);
                    if (!resolved.path("in").asText().equals("path")) {
                        continue;
                    }
                    assertTrue(actual.add(resolved.path("name").asText()), "Duplicate path parameter");
                    assertTrue(resolved.path("required").asBoolean());
                    assertFalse(resolved.path("description").asText().isBlank());
                    assertEquals("integer", resolved.at("/schema/type").asText());
                    assertEquals("int64", resolved.at("/schema/format").asText());
                    assertEquals(1, resolved.at("/schema/minimum").asInt());
                }
                assertEquals(expected, actual, path.getKey());
            });
        });
    }

    private void assertRequestMatchesDto(JsonNode document, Class<?> dto) throws Exception {
        JsonNode schema = document.path("components").path("schemas").path(dto.getSimpleName());
        assertEquals("object", schema.path("type").asText());
        Set<String> expectedFields = new HashSet<>();
        Set<String> expectedRequired = new HashSet<>();
        for (var component : dto.getRecordComponents()) {
            String name = component.getName();
            expectedFields.add(name);
            var field = dto.getDeclaredField(name);
            boolean required = field.isAnnotationPresent(NotNull.class) || field.isAnnotationPresent(NotBlank.class);
            if (required) {
                expectedRequired.add(name);
            }
            JsonNode property = schema.path("properties").path(name);
            String type = component.getType() == String.class ? "string"
                    : component.getType() == Boolean.class ? "boolean" : "integer";
            Set<String> types = property.path("type").isArray() ? strings(property.path("type"))
                    : Set.of(property.path("type").asText());
            assertEquals(required ? Set.of(type) : Set.of(type, "null"), types, dto.getSimpleName() + "." + name);
            if (type.equals("integer")) {
                assertEquals(component.getType() == Long.class ? "int64" : "int32", property.path("format").asText());
            }
            if (field.isAnnotationPresent(NotBlank.class)) {
                assertEquals(1, property.path("minLength").asInt());
                assertTrue(property.path("description").asText().contains("non-whitespace"));
            }
            if (field.isAnnotationPresent(Size.class)) {
                assertEquals(field.getAnnotation(Size.class).max(), property.path("maxLength").asInt());
            }
            if (field.isAnnotationPresent(Min.class)) {
                assertTrue(property.has("minimum"));
                assertEquals(field.getAnnotation(Min.class).value(), property.path("minimum").asLong());
            }
            if (field.isAnnotationPresent(Max.class)) {
                assertEquals(field.getAnnotation(Max.class).value(), property.path("maximum").asLong());
            }
            if (field.isAnnotationPresent(Positive.class)) {
                assertEquals(1, property.path("minimum").asLong());
            }
            if (field.isAnnotationPresent(jakarta.validation.constraints.Pattern.class)) {
                assertEquals(Set.of("vi", "en"), strings(property.path("enum")));
                assertEquals("^(vi|en)$", property.path("pattern").asText());
            }
        }
        assertEquals(expectedFields, fieldNames(schema.path("properties")), dto.getSimpleName());
        assertEquals(expectedRequired, strings(schema.path("required")), dto.getSimpleName());
    }

    private void assertEnvelopeContract(JsonNode document) {
        JsonNode schemas = document.at("/components/schemas");
        assertEquals(Set.of("success", "meta"), strings(schemas.at("/AdminEditResponse/required")));
        assertTrue(schemas.at("/AdminEditResponse/properties/success/const").asBoolean());
        assertEquals(Set.of("success", "error", "meta"), strings(schemas.at("/AdminEditErrorResponse/required")));
        assertEquals(false, schemas.at("/AdminEditErrorResponse/properties/success/const").booleanValue());
        for (String envelope : List.of("AdminEditResponse", "AdminEditErrorResponse")) {
            assertEquals("#/components/schemas/ResponseMeta", schemas.path(envelope).at("/properties/meta/$ref").asText());
        }
        assertEquals(Set.of("timestamp", "traceId", "correlationId"), strings(schemas.at("/ResponseMeta/required")));
        assertEquals("date-time", schemas.at("/ResponseMeta/properties/timestamp/format").asText());
        JsonNode error = resolve(document, schemas.at("/AdminEditErrorResponse/properties/error"));
        assertEquals(Set.of("code", "message", "details"), strings(error.path("required")));
        assertEquals("array", error.at("/properties/details/type").asText());
        assertEquals(Set.of("field", "message"), strings(error.at("/properties/details/items/required")));
    }

    private void assertLocalReferencesResolve(JsonNode document, JsonNode node) {
        if (node.has("$ref")) {
            String reference = node.path("$ref").asText();
            assertTrue(reference.startsWith("#/"), reference);
            assertFalse(document.at(reference.substring(1)).isMissingNode(), "Unresolved reference: " + reference);
        }
        node.forEach(child -> assertLocalReferencesResolve(document, child));
    }

    private JsonNode resolve(JsonNode document, JsonNode node) {
        return node.has("$ref") ? document.at(node.path("$ref").asText().substring(1)) : node;
    }

    private Set<String> strings(JsonNode array) {
        Set<String> result = new HashSet<>();
        array.forEach(value -> result.add(value.asText()));
        return result;
    }

    private Set<String> fieldNames(JsonNode object) {
        Set<String> result = new HashSet<>();
        object.fieldNames().forEachRemaining(result::add);
        return result;
    }
}
