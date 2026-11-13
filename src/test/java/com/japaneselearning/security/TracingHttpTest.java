package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class TracingHttpTest {
    private static final String TRACE = "abcdef0123456789abcdef0123456789";
    private static final String PARENT = "00-" + TRACE + "-0123456789abcdef-00";

    @Test
    void unsampledContextSurvivesAuthenticationFailureWithIndependentCorrelation() {
        var response = given()
                .header("traceparent", PARENT)
                .header("X-Correlation-ID", "independent-correlation")
                .get("/api/v1/jlpt-levels");

        assertEquals(401, response.statusCode());
        assertEquals(TRACE, response.header("X-Distributed-Trace-Id"));
        assertEquals("independent-correlation", response.header("X-Correlation-ID"));
    }

    @Test
    void separateDuplicateTraceparentHeadersAreRejected() {
        var response = given()
                .header("traceparent", PARENT, PARENT)
                .get("/api/v1/jlpt-levels");

        assertEquals(401, response.statusCode());
        assertNotEquals(TRACE, response.header("X-Distributed-Trace-Id"));
    }
}
