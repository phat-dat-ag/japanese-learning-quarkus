package com.japaneselearning.common.web;

import io.quarkus.oidc.common.OidcRequestContextProperties;
import io.quarkus.oidc.common.OidcRequestFilter.OidcRequestFilterContext;
import io.vertx.mutiny.core.Vertx;
import io.vertx.mutiny.ext.web.client.WebClient;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class JwksCorrelationFilterTest {
    @ParameterizedTest
    @ValueSource(strings = {"valid-id", "", "bad value", "oversized"})
    void onlyAValidatedBoundRequestIsPropagated(String candidate) {
        var vertx = Vertx.vertx();
        var client = WebClient.create(vertx);

        String previous = MDC.get("correlationId");

        try {
            String id = candidate.equals("oversized") ? "a".repeat(65) : candidate;

            if (!id.isEmpty()) {
                MDC.put("correlationId", id);
            } else {
                MDC.remove("correlationId");
            }
            // Construct the real HTTP request without sending it or resolving the host.
            var request = client.getAbs("http://test.invalid/api/v1/jwks/user");
            var context = new OidcRequestFilterContext(
                    request,
                    null,
                    new OidcRequestContextProperties()
            );

            new JwksCorrelationFilter().filter(context).await().indefinitely();

            assertEquals(
                    candidate.equals("valid-id") ? candidate : null,
                    request.headers().get(CorrelationIdFilter.HEADER)
            );
            assertNull(request.headers().get("Authorization"));
            assertNull(request.headers().get("Cookie"));
            assertEquals(id.isEmpty() ? null : id, MDC.get("correlationId"));
        } finally {
            if (previous == null) {
                MDC.remove("correlationId");
            } else {
                MDC.put("correlationId", previous);
            }

            client.close();
            vertx.close().await().indefinitely();
        }
    }
}
