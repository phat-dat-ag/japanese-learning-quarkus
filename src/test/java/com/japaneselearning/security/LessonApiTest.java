package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class LessonApiTest {
    RsaJsonWebKey signingKey;

    @ParameterizedTest
    @CsvSource({"POST,/api/v1/lessons", "PUT,/api/v1/lessons/1", "DELETE,/api/v1/lessons/1",
            "PUT,/api/v1/lessons/1/content", "DELETE,/api/v1/lessons/1/content?expectedVersion=1"})
    void mutationsRequireAdmin(String method, String path) throws Exception {
        given().contentType("application/json").body("{}").request(method, path)
                .then().statusCode(401).body("success", equalTo(false), "meta.traceId", notNullValue());
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .contentType("application/json").body("{}").request(method, path)
                .then().statusCode(403).body("success", equalTo(false), "meta.traceId", notNullValue());
    }

    @Test
    void grammarImportRequiresAdmin() throws Exception {
        byte[] file = "[]".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        given().multiPart("file", "lessons.json", file, "application/json")
                .post("/api/v1/lessons/content/import").then().statusCode(401);
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .multiPart("file", "lessons.json", file, "application/json")
                .post("/api/v1/lessons/content/import").then().statusCode(403);
    }

    @Test
    void runtimeOpenApiDefinesOneTypedContentContractAndRevisionPreconditions() {
        var doc = given().accept("application/json").get("/q/openapi").then().statusCode(200).extract().jsonPath();
        String path = "paths.'/api/v1/lessons/{lessonId}/content'";
        for (String method : List.of("get", "put", "delete")) {
            assertEquals(List.of(Map.of("bearerAuth", List.of())), doc.getList(path + "." + method + ".security"));
            for (String status : List.of("200", "400", "401", "403", "404", "500")) {
                assertNotNull(doc.getMap(path + "." + method + ".responses.'" + status + "'"));
            }
        }
        assertNotNull(doc.getMap(path + ".put.responses.'409'"));
        assertNotNull(doc.getMap(path + ".get.responses.'200'.headers.ETag"));
        assertEquals(8, doc.getList("components.schemas.LessonContentBlock.oneOf").size());
        assertEquals(false, doc.getBoolean("components.schemas.LessonContentDocument.additionalProperties"));
        assertEquals(200, doc.getInt("components.schemas.LessonContentDocument.properties.blocks.maxItems"));
        String upload = "paths.'/api/v1/lessons/content/import'.post";
        assertEquals("importLessonContents", doc.getString(upload + ".operationId"));
        assertEquals("binary", doc.getString(upload + ".requestBody.content.'multipart/form-data'.schema.properties.file.format"));
        assertTrue(doc.getString(upload + ".description").contains("UPDATE_EXISTING"));
        assertEquals("#/components/schemas/LessonContentImportResponse", doc.getString(upload
                + ".responses.'200'.content.'application/json'.schema.allOf[1].properties.data.'$ref'"));
    }

    @ParameterizedTest
    @CsvSource({"POST,/api/v1/lessons", "PUT,/api/v1/lessons/1"})
    void rejectsInvalidRequests(String method, String path) throws Exception {
        String token = new JwtTestTokens(signingKey).token("Admin");
        for (String body : List.of("{}", "null", "{", "[]")) {
            given().auth().oauth2(token).contentType("application/json").body(body)
                    .request(method, path).then().statusCode(400);
        }
        Map<String, Object> valid = Map.of("levelId", 1, "lessonNumber", 1,
                "title", "Title", "description", "Description", "displayOrder", 1);
        Map<String, List<Object>> invalid = Map.of(
                "levelId", List.of(0, -1), "lessonNumber", List.of(0, -1),
                "title", List.of("", "   ", "x".repeat(201)),
                "description", List.of("x".repeat(1001)), "displayOrder", List.of(0, -1));
        for (var field : invalid.entrySet()) {
            for (Object value : field.getValue()) {
                var body = new HashMap<>(valid);
                body.put(field.getKey(), value);
                given().auth().oauth2(token).contentType("application/json").body(body)
                        .request(method, path).then().statusCode(400).body("success", equalTo(false));
            }
        }
        for (String field : List.of("levelId", "lessonNumber", "title", "displayOrder")) {
            var body = new HashMap<>(valid);
            body.put(field, null);
            given().auth().oauth2(token).contentType("application/json").body(body)
                    .request(method, path).then().statusCode(400);
        }
        if (method.equals("PUT")) {
            given().auth().oauth2(token).contentType("application/json").body(valid)
                    .put("/api/v1/lessons/0").then().statusCode(400);
        }
    }

    @Test
    void getRemainsAvailableToLearnersWithExistingResponse() throws Exception {
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .get("/api/v1/lessons?level=N5").then().statusCode(200)
                .body("data[0].id", equalTo(1), "data[0].lessonNumber", equalTo(1),
                        "data[0].levelId", equalTo(17), "data[0].displayOrder", equalTo(9),
                        "data[0].title", equalTo("Lesson 1"), "data[0].description", equalTo("Test lesson"));
    }

    @ParameterizedTest
    @CsvSource({"post,/api/v1/lessons", "put,/api/v1/lessons/{lessonId}"})
    void runtimeOpenApiDocumentsFullMutationContract(String method, String path) {
        var doc = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();
        String op = "paths.'" + path + "'." + method;
        assertEquals(List.of(Map.of("bearerAuth", List.of())), doc.getList(op + ".security"));
        assertTrue(doc.getBoolean(op + ".requestBody.required"));
        assertEquals("#/components/schemas/LessonWriteRequest", doc.getString(
                op + ".requestBody.content.'application/json'.schema.'$ref'"));
        assertEquals("#/components/schemas/LessonResponse", doc.getString(
                op + ".responses.'200'.content.'application/json'.schema.allOf[1].properties.data.'$ref'"));
        for (String status : List.of("400", "401", "403", "404", "409", "500")) {
            assertNotNull(doc.getMap(op + ".responses.'" + status + "'"));
        }
        assertEquals(Set.of("id", "code", "name"),
                Set.copyOf(doc.getList("components.schemas.JlptLevelResponse.required")));
        assertEquals("int64", doc.getString("components.schemas.JlptLevelResponse.properties.id.format"));
        assertEquals(Set.of("id", "lessonNumber", "title", "levelId", "displayOrder"),
                Set.copyOf(doc.getList("components.schemas.LessonResponse.required")));
        assertEquals("int64", doc.getString("components.schemas.LessonResponse.properties.levelId.format"));
        assertEquals("int32", doc.getString("components.schemas.LessonResponse.properties.displayOrder.format"));
        String schema = "components.schemas.LessonWriteRequest";
        assertEquals(Set.of("levelId", "lessonNumber", "title", "displayOrder"),
                Set.copyOf(doc.getList(schema + ".required")));
        for (String field : List.of("levelId", "lessonNumber", "displayOrder")) {
            assertEquals(1, doc.getInt(schema + ".properties." + field + ".minimum"));
        }
        assertEquals(200, doc.getInt(schema + ".properties.title.maxLength"));
        assertEquals(1000, doc.getInt(schema + ".properties.description.maxLength"));
        assertEquals(List.of("string", "null"), doc.getList(schema + ".properties.description.type"));
        assertEquals(Set.of("id", "lessonNumber", "title", "description", "levelId", "displayOrder"),
                doc.getMap("components.schemas.LessonResponse.properties").keySet());
        if (method.equals("put")) {
            assertNotNull(doc.getMap("paths.'" + path + "'.delete"));
        }
        if (method.equals("put")) {
            assertEquals("lessonId", doc.getString(op + ".parameters[0].name"));
            assertEquals(1, doc.getInt(op + ".parameters[0].schema.minimum"));
        }
    }
}
