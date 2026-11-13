package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.quarkus.arc.Unremovable;
import io.quarkus.oidc.BearerTokenAuthentication;
import io.quarkus.oidc.common.OidcEndpoint;
import io.quarkus.oidc.common.OidcRequestFilter;
import io.quarkus.oidc.common.OidcResponseFilter;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Actual JWKS attempts and observed responses only; no cache-hit or token inspection.
 */
@ApplicationScoped
@Unremovable
@BearerTokenAuthentication
@OidcEndpoint(OidcEndpoint.Type.JWKS)
public class JwksMetricsFilter implements OidcRequestFilter, OidcResponseFilter {
    private static final String ATTEMPT = JwksMetricsFilter.class.getName();
    private final MeterRegistry registry;

    public JwksMetricsFilter(MeterRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Uni<Void> filter(OidcRequestFilterContext context) {
        context.contextProperties().put(ATTEMPT, new Attempt(System.nanoTime()));

        registry.counter(
                "app.dependency.requests",
                "dependency", "jwks-api", "operation", "jwks.fetch"
        ).increment();

        return Uni.createFrom().voidItem();
    }

    @Override
    public Uni<Void> filter(OidcResponseFilterContext context) {
        Attempt attempt = context.requestProperties().get(ATTEMPT, Attempt.class);

        if (attempt != null && attempt.completed.compareAndSet(false, true)) {
            String outcome = context.statusCode() >= 200 && context.statusCode() < 300
                    ? "success" : "failure";

            Timer.builder("app.dependency.duration")
                    .tags(
                            "dependency", "jwks-api",
                            "operation", "jwks.fetch",
                            "outcome", outcome
                    )
                    .serviceLevelObjectives(
                            Duration.ofMillis(10),
                            Duration.ofMillis(50),
                            Duration.ofMillis(100),
                            Duration.ofMillis(250),
                            Duration.ofMillis(500),
                            Duration.ofSeconds(1),
                            Duration.ofSeconds(2),
                            Duration.ofMillis(2500),
                            Duration.ofSeconds(5),
                            Duration.ofSeconds(10)
                    )
                    .register(registry)
                    .record(Duration.ofNanos(System.nanoTime() - attempt.started));
        }
        // Transport failures have no response callback: attempts minus responses is unresolved,
        // not a fabricated failure count. There is no global per-request map or pending gauge.
        return Uni.createFrom().voidItem();
    }

    private static final class Attempt {
        private final long started;
        private final AtomicBoolean completed = new AtomicBoolean();

        private Attempt(long started) {
            this.started = started;
        }
    }
}
