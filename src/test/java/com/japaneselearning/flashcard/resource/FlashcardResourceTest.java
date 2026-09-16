package com.japaneselearning.flashcard.resource;

import com.japaneselearning.security.JwksTestResource;
import com.japaneselearning.security.JwtTestTokens;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.restassured.specification.RequestSpecification;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(JwksTestResource.class)
@TestProfile(FlashcardApiTestProfile.class)
class FlashcardResourceTest {

    RsaJsonWebKey signingKey;
    private String accessToken;

    @BeforeEach
    void authenticateApplicationUser() throws Exception {
        accessToken = new JwtTestTokens(signingKey).token("User");
    }

    private RequestSpecification authenticatedRequest() {
        return given().auth().oauth2(accessToken);
    }

    @Test
    void preservesTraceAndCorrelationIdsOnSuccess() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", "trace-success")
                .header("X-Correlation-Id", "correlation-success")
                .when().get("/api/v1/flashcards");

        response.then().statusCode(200)
                .body("success", equalTo(true))
                .body("data.flashcardItems[0].id", equalTo(42));
        assertTracing(response, "trace-success", "correlation-success");
    }

    @Test
    void generatesIdsForSuccessAndErrorResponses() {
        Response success = authenticatedRequest().when().get("/api/v1/flashcards");
        Response error = authenticatedRequest().when().get("/api/v1/flashcards/999");

        success.then().statusCode(200);
        error.then().statusCode(404).body("error.code", equalTo("VOCABULARY_NOT_FOUND"));
        assertGeneratedTracing(success);
        assertGeneratedTracing(error);
        assertNotEquals(success.header("X-Correlation-Id"), error.header("X-Correlation-Id"));
    }

    @Test
    void returnsDetailedValidationErrorsWithCorrelationId() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", "trace-validation")
                .header("X-Correlation-Id", "correlation-validation")
                .queryParam("level", "N6")
                .queryParam("lesson", 0)
                .queryParam("page", -1)
                .queryParam("size", 101)
                .when().get("/api/v1/flashcards");

        response.then().statusCode(400)
                .body("success", equalTo(false))
                .body("error.code", equalTo("FLASHCARD_VALIDATION_ERROR"))
                .body("error.details.field", hasItems("level", "lesson", "page", "size"));
        assertTracing(response, "trace-validation", "correlation-validation");
    }

    @Test
    void rejectsNonPositiveVocabularyId() {
        Response response = authenticatedRequest().when().get("/api/v1/flashcards/0");

        response.then().statusCode(400)
                .body("error.code", equalTo("FLASHCARD_VALIDATION_ERROR"))
                .body("error.details[0].field", equalTo("id"));
        assertGeneratedTracing(response);
    }

    @Test
    void rejectsPaginationOverflow() {
        authenticatedRequest().queryParam("page", Integer.MAX_VALUE).queryParam("size", 100)
                .when().get("/api/v1/flashcards")
                .then().statusCode(400)
                .body("error.details[0].field", equalTo("page"));
    }

    @Test
    void returnsVocabularyNotFoundWithCorrelationId() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", "trace-missing")
                .header("X-Correlation-Id", "correlation-missing")
                .when().get("/api/v1/flashcards/999");

        response.then().statusCode(404)
                .body("success", equalTo(false))
                .body("error.code", equalTo("VOCABULARY_NOT_FOUND"))
                .body("error.message", equalTo("Vocabulary 999 not found"));
        assertTracing(response, "trace-missing", "correlation-missing");
    }

    @Test
    void hidesUnexpectedFailureDetailsAndPreservesCorrelationId() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", "trace-failure")
                .header("X-Correlation-Id", "correlation-failure")
                .when().get("/api/v1/flashcards/500");

        response.then().statusCode(500)
                .body("error.code", equalTo("INTERNAL_SERVER_ERROR"))
                .body("error.message", equalTo("An unexpected error occurred"))
                .body(not(containsString("Database connection details")));
        assertTracing(response, "trace-failure", "correlation-failure");
    }

    @Test
    void tracesRoutingErrorsBeforeResourceMatching() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", "trace-routing")
                .header("X-Correlation-Id", "correlation-routing")
                .when().get("/api/v1/route-that-does-not-exist");

        response.then().statusCode(404).body("success", equalTo(false));
        assertTracing(response, "trace-routing", "correlation-routing");
    }

    @Test
    void tracesMalformedQueryParameters() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", "trace-malformed")
                .header("X-Correlation-Id", "correlation-malformed")
                .queryParam("size", "invalid")
                .when().get("/api/v1/flashcards");

        response.then().statusCode(404).body("success", equalTo(false));
        assertTracing(response, "trace-malformed", "correlation-malformed");
    }

    @Test
    void hidesServerWebExceptionDetailsAndPreservesStatus() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", "trace-unavailable")
                .header("X-Correlation-Id", "correlation-unavailable")
                .when().get("/api/v1/flashcards/503");

        response.then().statusCode(503)
                .body("error.code", equalTo("INTERNAL_SERVER_ERROR"))
                .body("error.message", equalTo("An unexpected error occurred"))
                .body(not(containsString("Internal service details")));
        assertTracing(response, "trace-unavailable", "correlation-unavailable");
    }

    @Test
    void generatesIdsWhenIncomingHeadersAreBlank() {
        Response response = authenticatedRequest()
                .header("X-Trace-Id", " ")
                .header("X-Correlation-Id", " ")
                .when().get("/api/v1/flashcards");

        response.then().statusCode(200);
        assertGeneratedTracing(response);
    }

    private static void assertGeneratedTracing(Response response) {
        String traceId = response.header("X-Trace-Id");
        String correlationId = response.header("X-Correlation-Id");
        assertNotNull(traceId);
        assertNotNull(correlationId);
        assertFalse(traceId.isBlank());
        assertFalse(correlationId.isBlank());
        assertTracing(response, traceId, correlationId);
    }

    private static void assertTracing(Response response, String traceId, String correlationId) {
        assertEquals(traceId, response.header("X-Trace-Id"));
        assertEquals(correlationId, response.header("X-Correlation-Id"));
        assertEquals(traceId, response.jsonPath().getString("meta.traceId"));
        assertEquals(correlationId, response.jsonPath().getString("meta.correlationId"));
    }
}
