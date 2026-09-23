package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class ApplicationMetricsTest {
    RsaJsonWebKey signingKey;

    @Test
    void exportsRuntimeAndBoundedHttpMetricsIncludingAuthErrors() throws Exception {
        String token = new JwtTestTokens(signingKey).token("User");
        given().auth().oauth2(token).header("X-Correlation-ID", "metrics-correlation-sentinel")
                .queryParam("secret", "credential-sentinel").get("/api/v1/flashcards/987654321")
                .then().statusCode(200).header("X-Correlation-ID", "metrics-correlation-sentinel");
        given().get("/api/v1/jlpt-levels").then().statusCode(401);
        given().auth().oauth2(new JwtTestTokens(signingKey).token("Guest"))
                .get("/api/v1/jlpt-levels").then().statusCode(403);
        given().get("/error-probe/unexpected").then().statusCode(500);
        for (int i = 0; i < 20; i++) {
            given().get("/unmatched-credential-sentinel/" + i).then().statusCode(404);
        }
        String metrics = given().accept("text/plain").get("/q/metrics").then().statusCode(200)
                .extract().asString();
        assertTrue(metrics.contains("http_server_requests_seconds_count"));
        assertTrue(metrics.contains("http_server_requests_seconds_bucket"));
        assertTrue(metrics.contains("route=\"flashcards\""));
        for (String status : new String[]{"200", "401", "403", "404", "500"}) {
            assertTrue(metrics.contains("status=\"" + status + "\""), "Missing status " + status);
        }
        assertTrue(metrics.contains("jvm_memory_used_bytes"));
        assertTrue(metrics.contains("process_cpu_usage"));
        for (String sensitive : new String[]{token, "credential-sentinel", "metrics-correlation-sentinel",
                "987654321", "trace_id", "span_id", "user_id", "uri=", "clientName="}) {
            assertFalse(metrics.contains(sensitive), "Unsafe metrics field");
        }
    }
}
