package com.japaneselearning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.vocabulary.service.LessonBatchFileReader;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class LessonBatchApiTest {
    private static final String PATH = "/api/v1/lessons/batch";
    RsaJsonWebKey signingKey;

    @Test
    void requiresCaseSensitiveAdminRole() throws Exception {
        given().multiPart(
                        "file", "lessons.json", "[{}]".getBytes(StandardCharsets.UTF_8), "application/json"
                )
                .post(PATH).then().statusCode(401);

        for (String role : List.of("User", "admin")) {
            given().auth()
                    .oauth2(new JwtTestTokens(signingKey).token(role))
                    .multiPart(
                            "file",
                            "lessons.json",
                            "[{}]".getBytes(StandardCharsets.UTF_8),
                            "application/json"
                    )
                    .post(PATH).then().statusCode(403);
        }
    }

    @Test
    void rejectsInvalidEnvelopesBeforeProcessing() throws Exception {
        String token = new JwtTestTokens(signingKey).token("Admin");

        for (String body : List.of("", " ", "null", "[]", "{}", "[", "[{}] {}", "[1]", "[{\"levelId\":{}}]")) {
            given().auth().oauth2(token)
                    .multiPart(
                            "file",
                            "lessons.json",
                            body.getBytes(StandardCharsets.UTF_8),
                            "application/json"
                    )
                    .post(PATH)
                    .then()
                    .statusCode(400)
                    .body("success", equalTo(false), "error.code", equalTo("BAD_REQUEST"));
        }

        given()
                .auth()
                .oauth2(token)
                .multiPart(
                        "file",
                        "lessons.json",
                        new ObjectMapper().writeValueAsBytes(Collections.nCopies(101, Map.of())),
                        "application/json"
                )
                .post(PATH)
                .then()
                .statusCode(400);
    }

    @Test
    void allInvalidItemsStillReturn200AndOrderedResults() throws Exception {
        given().auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .multiPart(
                        "file",
                        "lessons.json",
                        "[{},null,{\"title\":\" \"}]".getBytes(StandardCharsets.UTF_8),
                        "application/json"
                ).post(PATH)
                .then().statusCode(200).body(
                        "success", equalTo(true),
                        "data.total", equalTo(3),
                        "data.succeeded", equalTo(0),
                        "data.failed", equalTo(3),
                        "data.results.index", equalTo(List.of(0, 1, 2)),
                        "data.results.success", equalTo(List.of(false, false, false)),
                        "data.results.error.code", equalTo(List.of("BAD_REQUEST", "BAD_REQUEST", "BAD_REQUEST"))
                );
    }

    @Test
    void acceptsMaximumSize() throws Exception {
        given().auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .multiPart(
                        "file",
                        "lessons.json",
                        new ObjectMapper().writeValueAsBytes(Collections.nCopies(100, Map.of())),
                        "application/json"
                ).post(PATH)
                .then().statusCode(200).body(
                        "data.total",
                        equalTo(100),
                        "data.failed",
                        equalTo(100)
                );
    }

    @Test
    void rejectsMissingAndOversizedFiles() throws Exception {
        String token = new JwtTestTokens(signingKey).token("Admin");

        given().auth().oauth2(token).multiPart("other", "ignored").post(PATH)
                .then().statusCode(400).body("error.code", equalTo("BAD_REQUEST"));

        given().auth().oauth2(token).multiPart(
                        "file", "lessons.json", new byte[LessonBatchFileReader.MAX_FILE_BYTES + 1], "application/json"
                )
                .post(PATH).then().statusCode(413).body("error.code", equalTo("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void parsesContentRegardlessOfFilenameAndMediaType() throws Exception {
        given().auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .multiPart("file", "lessons.txt", "[{}]".getBytes(StandardCharsets.UTF_8), "text/plain")
                .post(PATH).then().statusCode(200).body("data.total", equalTo(1), "data.failed", equalTo(1));
    }

    @Test
    void acceptsFileAtSizeLimit() throws Exception {
        byte[] content = ("[{}]" + " ".repeat(LessonBatchFileReader.MAX_FILE_BYTES - 4))
                .getBytes(StandardCharsets.UTF_8);

        given().auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .multiPart("file", "lessons.json", content, "application/json")
                .post(PATH).then().statusCode(200).body("data.total", equalTo(1));
    }

    @Test
    void rejectsOldJsonRequestContract() throws Exception {
        given().auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .contentType("application/json").body("[{}]").post(PATH).then().statusCode(415);
    }

    @Test
    void runtimeOpenApiDocumentsBatchContract() {
        var doc = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();

        String op = "paths.'" + PATH + "'.post";

        assertEquals(List.of(Map.of("bearerAuth", List.of())), doc.getList(op + ".security"));
        assertTrue(doc.getBoolean(op + ".requestBody.required"));
        assertEquals("createLessons", doc.getString(op + ".operationId"));
        assertEquals(Set.of("multipart/form-data"), doc.getMap(op + ".requestBody.content").keySet());
        String schema = op + ".requestBody.content.'multipart/form-data'.schema";
        assertEquals("object", doc.getString(schema + ".type"));
        assertEquals(List.of("file"), doc.getList(schema + ".required"));
        assertEquals("string", doc.getString(schema + ".properties.file.type"));
        assertEquals("binary", doc.getString(schema + ".properties.file.format"));
        assertTrue(doc.getString(schema + ".properties.file.description").contains("LessonWriteRequest"));
        assertEquals("#/components/schemas/LessonBatchResponse", doc.getString(op
                + ".responses.'200'.content.'application/json'.schema.allOf[1].properties.data.'$ref'"));

        for (String status : List.of("200", "400", "401", "403", "413", "415", "500")) {
            assertNotNull(doc.getMap(op + ".responses.'" + status + "'"));
        }

        assertNull(doc.getMap(op + ".responses.'409'"));
        assertTrue(doc.getString(op + ".description").contains("some or all items failed"));
        assertEquals("#/components/schemas/LessonResponse", doc.getString(
                "components.schemas.LessonBatchResult.properties.lesson.'$ref'"));
        assertEquals("#/components/schemas/ErrorResponse", doc.getString(
                "components.schemas.LessonBatchResult.properties.error.'$ref'"));
        assertEquals(0, doc.getInt("components.schemas.LessonBatchResult.properties.index.minimum"));
    }
}
