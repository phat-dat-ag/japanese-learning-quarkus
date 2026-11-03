package com.japaneselearning.security;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.jose4j.jwk.JsonWebKey;
import org.jose4j.jwk.JsonWebKeySet;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jose4j.jwk.RsaJwkGenerator;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class JwksTestResource implements QuarkusTestResourceLifecycleManager {

    private HttpServer server;
    private RsaJsonWebKey signingKey;
    private final AtomicInteger requests = new AtomicInteger();
    private volatile byte[] jwks;
    private volatile boolean unavailable;

    public void setUnavailable(boolean value) {
        unavailable = value;
    }

    public void publish(RsaJsonWebKey... keys) {
        jwks = new JsonWebKeySet(keys)
                .toJson(JsonWebKey.OutputControlLevel.PUBLIC_ONLY)
                .getBytes(StandardCharsets.UTF_8);
    }

    @Override
    public Map<String, String> start() {
        try {
            signingKey = RsaJwkGenerator.generateJwk(2048);
            signingKey.setKeyId("japanese-learning-test-1");
            signingKey.setUse("sig");
            signingKey.setAlgorithm("RS256");
            RsaJsonWebKey otherKey = RsaJwkGenerator.generateJwk(2048);
            otherKey.setKeyId("japanese-learning-test-2");
            otherKey.setUse("sig");
            otherKey.setAlgorithm("RS256");
            publish(otherKey, signingKey);
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/.well-known/jwks.json", exchange -> {
                requests.incrementAndGet();

                if (unavailable) {
                    exchange.sendResponseHeaders(503, -1);
                    exchange.close();

                    return;
                }

                byte[] response = jwks;

                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, jwks.length);
                try (var output = exchange.getResponseBody()) {
                    output.write(jwks);
                } finally {
                    exchange.close();
                }
            });
            server.start();
            String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
            return Map.of(
                    "quarkus.oidc.auth-server-url", baseUrl,
                    "quarkus.oidc.jwks-path", baseUrl + "/.well-known/jwks.json",
                    "quarkus.oidc.token.issuer", "JapaneseLearning.User",
                    "quarkus.oidc.token.audience", "JapaneseLearning"
            );
        } catch (Exception exception) {
            stop();
            throw new IllegalStateException("Cannot start test JWKS server", exception);
        }
    }

    @Override
    public void inject(TestInjector injector) {
        injector.injectIntoFields(signingKey, new TestInjector.MatchesType(RsaJsonWebKey.class));
        injector.injectIntoFields(requests, new TestInjector.MatchesType(AtomicInteger.class));
        injector.injectIntoFields(this, new TestInjector.MatchesType(JwksTestResource.class));
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop(0);
        }
    }
}
