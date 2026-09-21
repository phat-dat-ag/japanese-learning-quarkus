package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;
import io.restassured.response.Response;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class ProductionErrorTest {
    RsaJsonWebKey signingKey;

    @Test
    void exceptionsAndFrameworkErrorsAreSafeAndCorrelated() {
        check(given().header("X-Correlation-ID", "error-contract").get("/error-probe/missing"), 404, "NOT_FOUND");
        check(given().header("X-Correlation-ID", "error-contract").get("/error-probe/bad"), 400, "BAD_REQUEST");
        check(given().header("X-Correlation-ID", "error-contract").get("/error-probe/conflict"), 409, "TEST_CONFLICT");
        check(given().header("X-Correlation-ID", "error-contract").get("/error-probe/unexpected"), 500, "INTERNAL_SERVER_ERROR");
        check(given().header("X-Correlation-ID", "error-contract").get("/error-probe/invalid-return"), 500, "INTERNAL_SERVER_ERROR");
        Response busy = given().header("X-Correlation-ID", "error-contract").get("/error-probe/busy");
        check(busy, 503, "INTERNAL_SERVER_ERROR");
        assertEquals("5", busy.header("Retry-After"));
        Response method = given().header("X-Correlation-ID", "error-contract").get("/error-probe/method");
        check(method, 405, "METHOD_NOT_ALLOWED");
        assertTrue(method.header("Allow").contains("GET"));
        for (String body : new String[]{"{\"name\":\"credential-sentinel\",\"count\":\"invalid\"}", "{}", "{invalid"}) {
            check(given().header("X-Correlation-ID", "error-contract").contentType("application/json")
                    .body(body).post("/error-probe"), 400, "BAD_REQUEST");
        }
    }

    @Test
    void authenticationAndAuthorizationKeepStatusAndChallenge() throws Exception {
        Response missing = given().header("X-Correlation-ID", "error-contract").get("/api/v1/jlpt-levels");
        check(missing, 401, "UNAUTHORIZED");
        assertNotNull(missing.header("WWW-Authenticate"));
        Response invalid = given().header("X-Correlation-ID", "error-contract").auth().oauth2("credential-sentinel")
                .get("/api/v1/jlpt-levels");
        check(invalid, 401, "UNAUTHORIZED");
        assertNotNull(invalid.header("WWW-Authenticate"));
        check(given().header("X-Correlation-ID", "error-contract").auth().oauth2(new JwtTestTokens(signingKey).token("Guest"))
                .get("/api/v1/jlpt-levels"), 403, "FORBIDDEN");
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .get("/api/v1/jlpt-levels").then().statusCode(200);
    }

    @Test
    void missingImportFileIsBadRequestAfterAuthorization() throws Exception {
        check(given().header("X-Correlation-ID", "error-contract")
                .auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .multiPart("other", "credential-sentinel").post("/api/vocabularies/import"), 400, "BAD_REQUEST");
    }

    @Test
    void logsUnexpectedFailuresOnceWithSafeCodeLocations() {
        var records = new java.util.concurrent.ConcurrentLinkedQueue<String>();
        var logger = java.util.logging.Logger.getLogger("com.japaneselearning.common.exception.handler.SafeExceptionLog");
        var handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                var extended = (org.jboss.logmanager.ExtLogRecord) record;
                records.add(extended.getFormattedMessage() + " correlationId=" + extended.getMdc("correlationId"));
                assertNull(record.getThrown());
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            given().header("X-Correlation-ID", "safe-log").get("/error-probe/conflict").then().statusCode(409);
            assertTrue(records.isEmpty());
            given().header("X-Correlation-ID", "safe-log").get("/error-probe/unexpected").then().statusCode(500);
            assertEquals(1, records.size());
            String log = records.element();
            assertTrue(log.contains("ErrorProbeResource.error"));
            assertTrue(log.contains("IllegalStateException"));
            assertTrue(log.contains("correlationId=safe-log"));
            assertFalse(log.contains("credential-sentinel"));
            assertFalse(log.contains("private_table"));
            assertFalse(log.contains("password="));
        } finally {
            logger.removeHandler(handler);
        }
    }

    private void check(Response response, int status, String code) {
        assertEquals(status, response.statusCode());
        assertEquals("error-contract", response.header("X-Correlation-ID"));
        assertTrue(response.contentType().startsWith("application/json"));
        String body = response.asString();
        assertFalse(body.contains("credential-sentinel"));
        assertFalse(body.contains("Exception"));
        assertFalse(body.contains("private_table"));
        assertFalse(response.jsonPath().getBoolean("success"));
        assertEquals(code, response.jsonPath().getString("error.code"));
        assertEquals("error-contract", response.jsonPath().getString("meta.correlationId"));
        assertNotNull(response.jsonPath().getString("meta.traceId"));
    }
}
