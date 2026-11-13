package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.quarkus.oidc.common.OidcRequestContextProperties;
import io.quarkus.oidc.common.OidcRequestFilter.OidcRequestFilterContext;
import io.quarkus.oidc.common.OidcResponseFilter.OidcResponseFilterContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JwksMetricsFilterTest {
    @Test
    void distinguishesAttemptsResponsesAndRepeatedCallbacksWithoutReadingPayloads() {
        var registry = new SimpleMeterRegistry();

        registry.config().meterFilter(new ApplicationMetricsFilter());

        try {
            var filter = new JwksMetricsFilter(registry);

            for (int status : new int[]{200, 503}) {
                var properties = new OidcRequestContextProperties();

                filter.filter(new OidcRequestFilterContext(null, null, properties))
                        .await().indefinitely();

                var response = new OidcResponseFilterContext(properties, status, null, null);

                filter.filter(response).await().indefinitely();
                filter.filter(response).await().indefinitely();
            }
            // A request with no response remains attempted, not confirmed or guessed failed.
            filter.filter(new OidcRequestFilterContext(
                    null, null, new OidcRequestContextProperties()
            )).await().indefinitely();

            assertEquals(3, registry.get("app.dependency.requests").counter().count());
            assertEquals(1, registry.get("app.dependency.duration")
                    .tag("outcome", "success").timer().count());
            assertEquals(1, registry.get("app.dependency.duration")
                    .tag("outcome", "failure").timer().count());
        } finally {
            registry.close();
        }
    }
}
