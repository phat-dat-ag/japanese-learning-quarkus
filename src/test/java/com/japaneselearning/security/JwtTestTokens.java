package com.japaneselearning.security;

import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jws.AlgorithmIdentifiers;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.jwt.JwtClaims;

import java.security.Key;
import java.time.Instant;
import java.util.UUID;

public class JwtTestTokens {

    private final RsaJsonWebKey signingKey;

    public JwtTestTokens(RsaJsonWebKey signingKey) {
        this.signingKey = signingKey;
    }

    public String token(String role) throws Exception {
        return sign(claims(role));
    }

    public JwtClaims claims(String role) {
        JwtClaims claims = new JwtClaims();
        claims.setSubject("f4385b76-5fe1-4bfc-8b96-198828c35f53");
        claims.setIssuer("JapaneseLearning.User");
        claims.setAudience("JapaneseLearning");
        claims.setClaim("exp", Instant.now().plusSeconds(300).getEpochSecond());
        claims.setStringClaim("unique_name", "test-user");
        claims.setStringClaim("email", "test@example.invalid");
        claims.setStringClaim("role", role);
        claims.setJwtId(UUID.randomUUID().toString());
        return claims;
    }

    public String sign(JwtClaims claims) throws Exception {
        return sign(claims, signingKey.getPrivateKey(), signingKey.getKeyId(),
                AlgorithmIdentifiers.RSA_USING_SHA256);
    }

    public String sign(JwtClaims claims, Key key, String kid, String algorithm) throws Exception {
        JsonWebSignature signature = new JsonWebSignature();
        signature.setAlgorithmHeaderValue(algorithm);
        signature.setKeyIdHeaderValue(kid);
        signature.setHeader("typ", "JWT");
        signature.setPayload(claims.toJson());
        signature.setKey(key);
        return signature.getCompactSerialization();
    }

}
