package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

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
        for (String status : new String[]{"200", "400", "401", "403", "404", "409"}) {
            org.junit.jupiter.api.Assertions.assertNotNull(
                    document.getMap(operation + ".responses.'" + status + "'"));
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
}
