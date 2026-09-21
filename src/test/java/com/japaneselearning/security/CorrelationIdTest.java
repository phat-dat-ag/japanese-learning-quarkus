package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class CorrelationIdTest {
    private static final String HEADER = "X-Correlation-ID";
    RsaJsonWebKey signingKey;

    @Test
    void preservesValidAndReplacesMissingInvalidAndDuplicateIds() throws Exception {
        String token = new JwtTestTokens(signingKey).token("User");
        for (String value : new String[]{null, "", "valid_ID-123", "bad value", "a".repeat(64), "a".repeat(65), "a,b"}) {
            var request = given().auth().oauth2(token);
            if (value != null) request.header(HEADER, value);
            var response = request.get("/api/v1/jlpt-levels");
            assertEquals(200, response.statusCode());
            String id = response.header(HEADER);
            if (value != null && value.matches("[A-Za-z0-9_-]{1,64}")) assertEquals(value, id);
            else assertTrue(id.matches("[a-f0-9]{32}"));
            assertEquals(id, response.jsonPath().getString("meta.correlationId"));
        }
        var duplicate = given().auth().oauth2(token).header(HEADER, "first", "second").get("/api/v1/jlpt-levels");
        assertEquals(200, duplicate.statusCode());
        assertTrue(duplicate.header(HEADER).matches("[a-f0-9]{32}"));
    }

    @Test
    void tracesEarlyAuthenticationFailuresAndHealth() {
        given().header(HEADER, "auth-rejected").auth().oauth2("malformed-token")
                .get("/api/v1/jlpt-levels").then().statusCode(401).header(HEADER, "auth-rejected");
        given().header(HEADER, "auth-missing").get("/api/v1/jlpt-levels")
                .then().statusCode(401).header(HEADER, "auth-missing");
        given().header(HEADER, "health-id").get("/q/health/live")
                .then().statusCode(200).header(HEADER, "health-id");
    }

    @Test
    void isolatesMdcAcrossConcurrentReactiveRequests() {
        var client = HttpClient.newHttpClient();
        var pending = new ArrayList<CompletableFuture<Void>>();
        for (int i = 0; i < 20; i++) {
            String id = "reactive-" + i;
            URI uri = URI.create("http://localhost:" + io.restassured.RestAssured.port + "/correlation-probe");
            var request = HttpRequest.newBuilder(uri).header(HEADER, id).build();
            pending.add(client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenAccept(response -> {
                assertEquals(200, response.statusCode());
                assertEquals(id, response.headers().firstValue(HEADER).orElseThrow());
                assertEquals(id, response.body());
            }));
        }
        CompletableFuture.allOf(pending.toArray(CompletableFuture[]::new)).join();
    }
}
