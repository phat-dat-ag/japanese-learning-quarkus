package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jwt.JwtClaims;
import org.jose4j.keys.HmacKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class JwtSecurityTest {

    private static final String LEVELS = "/api/v1/jlpt-levels";
    private static final String IMPORT = "/api/vocabularies/import";

    RsaJsonWebKey signingKey;
    AtomicInteger jwksRequests;
    private JwtTestTokens tokens;
    private static final String[] NORMAL_APIS = {
            LEVELS, "/api/v1/lessons?level=N5", "/api/v1/flashcards", "/api/v1/flashcards/42"
    };

    @BeforeEach
    void setUpTokens() {
        tokens = new JwtTestTokens(signingKey);
    }

    @Test
    void rejectsMissingToken() {
        for (String path : NORMAL_APIS) {
            given().get(path).then().statusCode(401);
        }
    }

    @Test
    void rejectsMalformedToken() {
        assertUnauthorized("not.a.jwt");
    }

    @Test
    void acceptsUserTokenWithoutIssuedAtAndCachesJwks() throws Exception {
        String token = tokens.token("User");
        given().auth().oauth2(token).get(LEVELS).then().statusCode(200)
                .body("success", equalTo(true)).body("data[0].code", equalTo("N5"));
        int requests = jwksRequests.get();
        assertTrue(requests > 0);
        given().auth().oauth2(token).get(LEVELS).then().statusCode(200);
        assertEquals(requests, jwksRequests.get(), "Known kid should use cached JWKS");
    }

    @Test
    void deniesUserImport() throws Exception {
        given().auth().oauth2(tokens.token("User"))
                .multiPart("file", "test.json", "[]".getBytes(StandardCharsets.UTF_8), "application/json")
                .post(IMPORT).then().statusCode(403);
    }

    @Test
    void acceptsAdminImport() throws Exception {
        given().auth().oauth2(tokens.token("Admin"))
                .multiPart("file", "test.json", "[]".getBytes(StandardCharsets.UTF_8), "application/json")
                .post(IMPORT).then().statusCode(200)
                .body("success", equalTo(true)).body("data.created", equalTo(1));
    }

    @Test
    void rejectsWrongIssuer() throws Exception {
        JwtClaims claims = tokens.claims("User");
        claims.setIssuer("another-issuer");
        assertUnauthorized(tokens.sign(claims));
    }

    @Test
    void rejectsWrongAudience() throws Exception {
        JwtClaims claims = tokens.claims("User");
        claims.setAudience("another-audience");
        assertUnauthorized(tokens.sign(claims));
    }

    @Test
    void rejectsExpiredToken() throws Exception {
        JwtClaims claims = tokens.claims("User");
        claims.setClaim("exp", Instant.now().minusSeconds(5).getEpochSecond());
        assertUnauthorized(tokens.sign(claims));
    }

    @Test
    void rejectsMissingExpiration() throws Exception {
        JwtClaims claims = tokens.claims("User");
        claims.unsetClaim("exp");
        assertUnauthorized(tokens.sign(claims));
    }

    @Test
    void rejectsNotYetValidToken() throws Exception {
        JwtClaims claims = tokens.claims("User");
        claims.setClaim("nbf", Instant.now().plusSeconds(300).getEpochSecond());
        assertUnauthorized(tokens.sign(claims));
    }

    @Test
    void rejectsWrongRsaKeyWithKnownKid() throws Exception {
        assertUnauthorized(tokens.sign(tokens.claims("User"), RsaJwkGenerator.generateJwk(2048).getPrivateKey(),
                signingKey.getKeyId(), AlgorithmIdentifiers.RSA_USING_SHA256));
    }

    @Test
    void rejectsUnknownKid() throws Exception {
        assertUnauthorized(tokens.sign(tokens.claims("User"), signingKey.getPrivateKey(),
                "unknown-key", AlgorithmIdentifiers.RSA_USING_SHA256));
    }

    @Test
    void rejectsHs256() throws Exception {
        assertUnauthorized(tokens.sign(tokens.claims("User"),
                new HmacKey(new byte[32]), signingKey.getKeyId(), AlgorithmIdentifiers.HMAC_SHA256));
    }

    @Test
    void rejectsRs512EvenWithTrustedKey() throws Exception {
        assertUnauthorized(tokens.sign(tokens.claims("User"), signingKey.getPrivateKey(),
                signingKey.getKeyId(), AlgorithmIdentifiers.RSA_USING_SHA512));
    }

    @Test
    void doesNotTreatGroupsAsAdminRole() throws Exception {
        JwtClaims claims = tokens.claims("User");
        claims.setStringListClaim("groups", "Admin");
        given().auth().oauth2(tokens.sign(claims))
                .multiPart("file", "test.json", "[]".getBytes(StandardCharsets.UTF_8), "application/json")
                .post(IMPORT).then().statusCode(403);
    }

    @ParameterizedTest
    @ValueSource(strings = {"User", "Admin"})
    void permitsApplicationRolesOnAllNormalApis(String role) throws Exception {
        String token = tokens.token(role);
        for (String path : NORMAL_APIS) {
            given().auth().oauth2(token).get(path).then().statusCode(200)
                    .body("success", equalTo(true));
        }
    }

    @Test
    void rejectsAuthenticatedNonApplicationRole() throws Exception {
        String token = tokens.token("Guest");
        for (String path : NORMAL_APIS) {
            given().auth().oauth2(token).get(path).then().statusCode(403);
        }
    }

    @Test
    void rejectsImportWithoutToken() {
        given().multiPart("file", "test.json", "[]".getBytes(StandardCharsets.UTF_8), "application/json")
                .post(IMPORT).then().statusCode(401);
    }

    private void assertUnauthorized(String token) {
        for (String path : NORMAL_APIS) {
            given().auth().oauth2(token).get(path).then().statusCode(401);
        }
    }
}
