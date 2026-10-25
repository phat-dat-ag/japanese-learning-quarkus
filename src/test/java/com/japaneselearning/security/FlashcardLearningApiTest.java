package com.japaneselearning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class FlashcardLearningApiTest {
    private static final String BASE = "/api/v1/flashcards";
    RsaJsonWebKey signingKey;

    @Test
    void everyPersonalRouteRequiresApplicationRole() throws Exception {
        var routes = List.of(
                new String[]{"POST", "/sessions"}, new String[]{"GET", "/sessions"},
                new String[]{"GET", "/sessions/1"}, new String[]{"POST", "/sessions/1/abandon"}, new String[]{"POST", "/sessions/1/finish"},
                new String[]{"PUT", "/sessions/1/items/1/review"}, new String[]{"GET", "/reviews/due"},
                new String[]{"GET", "/reviews/forgotten"}, new String[]{"GET", "/bookmarks"},
                new String[]{"PUT", "/1/bookmark"}, new String[]{"DELETE", "/1/bookmark"},
                new String[]{"GET", "/statistics"});
        for (String[] route : routes) {
            given().contentType("application/json").body("{}").request(route[0], BASE + route[1]).then().statusCode(401);
            given().auth().oauth2(token("Guest", "owner")).contentType("application/json").body("{}")
                    .request(route[0], BASE + route[1]).then().statusCode(403);
        }
    }

    @Test
    void rejectsInvalidSubjectsAndBodiesBeforePersistence() throws Exception {
        for (String subject : List.of(" ", "a".repeat(256))) {
            given().auth().oauth2(token("User", subject)).get(BASE + "/statistics").then().statusCode(401);
            given().auth().oauth2(token("Admin", subject)).get(BASE + "?includeLearning=true").then().statusCode(401);
        }
        String token = token("User", "owner");
        for (String body : List.of("null", "{}", "{", "{\"lessonId\":0}",
                "{\"lessonId\":1,\"cardLimit\":101}", "{\"lessonId\":1,\"mode\":\"UNKNOWN\"}")) {
            given().auth().oauth2(token).contentType("application/json").body(body).post(BASE + "/sessions")
                    .then().statusCode(400).body("success", equalTo(false));
        }
        for (String body : List.of("null", "{}", "{\"rating\":\"GOOD\"}",
                "{\"rating\":\"UNKNOWN\",\"expectedProgressVersion\":0}",
                "{\"rating\":\"AGAIN\",\"expectedProgressVersion\":-1}")) {
            given().auth().oauth2(token).contentType("application/json").body(body).put(BASE + "/sessions/1/items/1/review")
                    .then().statusCode(400).body("success", equalTo(false));
        }
    }

    @Test
    void mergedOpenApiDescribesPracticeEmptyStateAndBothPercentages() throws Exception {
        var document = new ObjectMapper().readTree(given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().asString());
        var schemas = document.path("components").path("schemas");
        var percentages = schemas.path("LearningStatisticsResponse").path("properties");
        assertTrue(percentages.has("studiedPercentage"));
        assertTrue(percentages.has("masteryPercentage"));
        assertFalse(schemas.path("FlashcardSessionCreateRequest").path("properties").has("userId"));
        assertEquals(100, schemas.path("FlashcardSessionCreateRequest").at("/properties/cardLimit/maximum").asInt());
        assertTrue(document.path("paths").path(BASE + "/sessions").path("post").path("description").asText().contains("NO_ELIGIBLE_CARDS"));
        assertTrue(document.path("paths").path(BASE + "/sessions").path("post").path("description").asText().contains("practice"));
        assertTrue(schemas.path("FlashcardListItemResponse").path("properties").has("progress"));
        assertEquals("int32", schemas.path("PersonalFlashcardPageResponse").at("/properties/page/format").asText());
        assertTrue(schemas.path("FlashcardSessionResponse").path("properties").has("abandonedAt"));
        assertTrue(schemas.path("FlashcardSessionResponse").at("/properties/status/enum").toString().contains("ABANDONED"));
        var abandon = document.path("paths").path(BASE + "/sessions/{id}/abandon").path("post");
        assertFalse(abandon.has("requestBody"));
        assertTrue(abandon.path("description").asText().contains("idempotent"));
        assertTrue(document.path("paths").path(BASE + "/sessions/{id}/items/{itemId}/review").path("put")
                .at("/responses/409/description").asText().contains("FLASHCARD_NOT_DUE"));
        assertFalse(document.at("/components/securitySchemes/bearerAuth/description").asText()
                .contains("write operations require the case-sensitive Admin"));
    }

    private String token(String role, String subject) throws Exception {
        var tokens = new JwtTestTokens(signingKey);
        var claims = tokens.claims(role);
        claims.setSubject(subject);
        return tokens.sign(claims);
    }
}
