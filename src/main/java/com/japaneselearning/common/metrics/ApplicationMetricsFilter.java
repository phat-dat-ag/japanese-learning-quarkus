package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.config.MeterFilter;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import jakarta.inject.Singleton;

import java.util.Set;

@Singleton
public class ApplicationMetricsFilter implements MeterFilter {
    private static final Set<String> METHODS = Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        String name = id.getName();
        return name.equals("http.server.requests") || name.startsWith("jvm.")
                || name.startsWith("process.") || name.startsWith("system.")
                ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        if (!id.getName().equals("http.server.requests")) {
            return id;
        }
        String method = id.getTag("method");
        String status = id.getTag("status");
        // Replace the entire HTTP tag set, including unknown routes and early 401s.
        return new Meter.Id(id.getName(), Tags.of(
                "method", method != null && METHODS.contains(method) ? method : "OTHER",
                "route", route(id.getTag("uri") != null ? id.getTag("uri") : id.getTag("route")),
                "status", status != null && status.matches("[1-5][0-9][0-9]") ? status : "OTHER"),
                id.getBaseUnit(), id.getDescription(), id.getType());
    }

    private static String route(String uri) {
        if (uri == null) {
            return "other";
        }
        if (Set.of("flashcards", "jlpt-levels", "lessons", "import", "other").contains(uri)) {
            return uri; // Composite and Prometheus registries can both apply the filter.
        }
        for (String route : new String[]{"flashcards", "jlpt-levels", "lessons"}) {
            String prefix = "/api/v1/" + route;
            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return route;
            }
        }
        return uri.equals("/api/vocabularies/import") ? "import" : "other";
    }

    @Override
    public DistributionStatisticConfig configure(Meter.Id id, DistributionStatisticConfig config) {
        if (id.getName().equals("http.server.requests")) {
            // Micrometer Timer boundaries are nanoseconds; export bounded Prometheus buckets.
            return DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(10e6, 50e6, 100e6, 250e6, 500e6, 1e9, 2.5e9, 5e9, 10e9)
                    .build().merge(config);
        }
        return config;
    }
}
