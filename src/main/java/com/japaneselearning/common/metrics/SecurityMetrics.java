package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.vertx.http.runtime.filters.Filters;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class SecurityMetrics {
    private final MeterRegistry registry;

    public SecurityMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    void register(
            @Observes
            Filters filters
    ) {
        // Observe completed responses, including proactive authentication rejection.
        filters.register(context -> {
            if (!context.normalizedPath().startsWith("/q/")) {
                context.addEndHandler(
                        ignored -> completed(
                                context.response().getStatusCode(),
                                context.user() != null
                        )
                );
            }

            context.next();
        }, Integer.MAX_VALUE - 1);
    }

    public void completed(int status, boolean authenticated) {
        if (status == 403) {
            record("authorization", "denied");
        } else if (status == 401) {
            record("authentication", "unauthenticated");
        } else if (authenticated && status >= 200 && status < 300) {
            record("authentication", "success");
        }
    }

    private void record(String operation, String outcome) {
        registry.counter(
                        "app.security.events",
                        "operation", operation,
                        "outcome", outcome
                )
                .increment();
    }
}
