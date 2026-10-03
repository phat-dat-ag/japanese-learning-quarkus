package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class OpenApiSecurityTest {

    @Test
    void exposesOneHttpBearerSchemeWithoutAuthentication() {
        JsonPath document = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();
        Map<String, Object> schemes = document.getMap("components.securitySchemes");
        assertEquals(Set.of("bearerAuth"), schemes.keySet());
        assertEquals("http", document.getString("components.securitySchemes.bearerAuth.type"));
        assertEquals("bearer", document.getString("components.securitySchemes.bearerAuth.scheme"));
        assertEquals("JWT", document.getString("components.securitySchemes.bearerAuth.bearerFormat"));
    }

    @Test
    void allApplicationOperationsRequireBearerAuth() {
        JsonPath document = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> globalSecurity = document.getList("security");
        List<Map<String, Object>> expectedSecurity = List.of(Map.of("bearerAuth", List.of()));
        assertEquals(expectedSecurity, globalSecurity);
        Map<String, Map<String, Object>> paths = document.getMap("paths");
        Map<String, String> operations = new java.util.HashMap<>(Map.of(
                "/api/v1/flashcards", "get",
                "/api/v1/flashcards/{id}", "get",
                "/api/v1/jlpt-levels", "get",
                "/api/v1/lessons", "get",
                "/api/vocabularies/import", "post",
                "/api/vocabularies", "post"
        ));
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/readings", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/readings/{readingId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/meanings", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/meanings/{meaningId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/pitch-accents", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/pitch-accents/{pitchAccentId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/examples", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/examples/{exampleId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/levels", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/levels/{levelId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/lessons", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/lessons/{lessonId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/parts-of-speech", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/kanji", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/kanji/{kanjiId}", "put");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/kanji/{kanjiId}/readings", "post");
        operations.put("/api/v1/admin/vocabularies/{vocabularyId}/kanji/{kanjiId}/readings/{readingId}", "put");
        operations.put("/api/v1/lessons/{lessonId}", "put");
        assertEquals(operations.keySet(), paths.keySet());
        operations.forEach((path, method) -> {
            Map<?, ?> operation = (Map<?, ?>) paths.get(path).get(method);
            // OpenAPI operations inherit root security unless they explicitly override it.
            Object security = operation.containsKey("security") ? operation.get("security") : globalSecurity;
            assertEquals(expectedSecurity, security, method + " " + path);
        });
    }

    @Test
    void documentsFlashcardDetailIdentitiesWithoutChangingLearnerFields() {
        JsonPath document = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();
        assertEquals("#/components/schemas/FlashcardDetailResponse", document.getString(
                "paths.'/api/v1/flashcards/{id}'.get.responses.'200'.content.'application/json'.schema.properties.data.'$ref'"));
        Map<String, String> identities = Map.of(
                "FlashcardReadingResponse", "readingId",
                "FlashcardMeaningResponse", "meaningId",
                "FlashcardPitchAccentResponse", "pitchAccentId",
                "FlashcardLevelResponse", "levelId",
                "FlashcardLessonResponse", "lessonId",
                "FlashcardExampleResponse", "exampleId",
                "FlashcardKanjiResponse", "kanjiId",
                "FlashcardKanjiReadingResponse", "kanjiReadingId");
        identities.forEach((schema, field) -> {
            String property = "components.schemas." + schema + ".properties." + field;
            assertEquals("integer", document.getString(property + ".type"));
            assertEquals("int64", document.getString(property + ".format"));
            assertEquals(1, document.getInt(property + ".minimum"));
        });
        assertEquals("integer", document.getString(
                "components.schemas.FlashcardReadingResponse.properties.pitchAccents.items.type"));
        assertEquals("#/components/schemas/FlashcardPitchAccentResponse", document.getString(
                "components.schemas.FlashcardReadingResponse.properties.pitchAccentDetails.items.'$ref'"));
        assertEquals("int32", document.getString(
                "components.schemas.FlashcardLessonResponse.properties.assignmentDisplayOrder.format"));
    }

    @Test
    void swaggerUiIsAccessibleWithoutAuthentication() {
        given().get("/q/swagger-ui/").then().statusCode(200)
                .body(containsString("swagger-ui-bundle.js"));
    }

    @Test
    void livenessIsAccessibleWithoutAuthentication() {
        given().get("/q/health/live").then().statusCode(200);
    }
}
