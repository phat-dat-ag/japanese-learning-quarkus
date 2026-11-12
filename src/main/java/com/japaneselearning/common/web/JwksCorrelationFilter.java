package com.japaneselearning.common.web;

import io.quarkus.oidc.common.OidcEndpoint;
import io.quarkus.oidc.common.OidcRequestFilter;
import io.smallrye.mutiny.Uni;
import org.jboss.logmanager.MDC;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

/**
 * Adds diagnostic correlation only; never reads or forwards JWTs or key bodies.
 */
@ApplicationScoped
@OidcEndpoint(OidcEndpoint.Type.JWKS)
public class JwksCorrelationFilter implements OidcRequestFilter {
    @Override
    public Uni<Void> filter(OidcRequestFilterContext context) {
        String current = MDC.get("correlationId");
        String correlation = CorrelationIdFilter.validatedId(
                current == null ? List.of() : List.of(current)
        );

        if (correlation != null) {
            context.request().putHeader(CorrelationIdFilter.HEADER, correlation);
        }

        // A startup/background refresh has no request identity; do not manufacture one.
        return Uni.createFrom().voidItem();
    }
}
