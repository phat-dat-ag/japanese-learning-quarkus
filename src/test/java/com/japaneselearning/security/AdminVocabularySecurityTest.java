package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class AdminVocabularySecurityTest {
    RsaJsonWebKey signingKey;

    @ParameterizedTest
    @CsvSource({"PUT,''", "POST,/readings", "PUT,/readings/1", "POST,/meanings", "PUT,/meanings/1",
            "POST,/examples", "PUT,/examples/1", "POST,/pitch-accents", "PUT,/pitch-accents/1",
            "POST,/levels", "PUT,/levels/1", "POST,/lessons", "PUT,/lessons/1", "POST,/parts-of-speech",
            "POST,/kanji", "PUT,/kanji/1", "POST,/kanji/1/readings", "PUT,/kanji/1/readings/1"})
    void allEditOperationsRequireAdmin(String method, String suffix) throws Exception {
        String path = "/api/v1/admin/vocabularies/1" + suffix;
        given().contentType("application/json").body("{}").request(method, path).then().statusCode(401);
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .contentType("application/json").body("{}").request(method, path).then().statusCode(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"readings", "meanings", "examples", "pitch-accents", "levels", "lessons",
            "parts-of-speech", "kanji", "kanji/1/readings"})
    void addRequiresANonemptyArrayOfValidItems(String section) throws Exception {
        String token = new JwtTestTokens(signingKey).token("Admin");
        for (String body : new String[]{"{}", "[]", "null", "[null]", "[{}]"}) {
            given().auth().oauth2(token).contentType("application/json").body(body)
                    .post("/api/v1/admin/vocabularies/1/" + section).then().statusCode(400);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"readings", "meanings", "examples", "pitch-accents", "levels", "lessons",
            "parts-of-speech", "kanji", "kanji/{kanjiId}/readings"})
    void openApiDocumentsArrayAndStatusContract(String section) {
        var document = given().accept("application/json").get("/q/openapi").then().statusCode(200).extract().jsonPath();
        String operation = "paths.'/api/v1/admin/vocabularies/{vocabularyId}/" + section + "'.post";
        assertEquals("array", document.getString(operation + ".requestBody.content.'application/json'.schema.type"));
        for (String status : new String[]{"200", "400", "401", "403", "404", "409"}) {
            org.junit.jupiter.api.Assertions.assertNotNull(document.getMap(operation + ".responses.'" + status + "'"));
        }
    }
}
