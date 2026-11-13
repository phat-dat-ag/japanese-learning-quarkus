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
    private static final Set<String> METHODS =
            Set.of("GET", "HEAD", "POST", "PUT", "PATCH", "DELETE", "OPTIONS");

    @Override
    public MeterFilterReply accept(Meter.Id id) {
        String name = id.getName();
        return name.equals("http.server.requests")
                || name.equals("http.client.requests")
                || name.equals("app.security.events")
                || name.equals("app.database.failures")
                || name.equals("app.dependency.requests")
                || name.equals("app.dependency.duration")
                || databaseMetric(name)
                || name.startsWith("jvm.")
                || name.startsWith("process.")
                || name.startsWith("system.")
                ? MeterFilterReply.NEUTRAL : MeterFilterReply.DENY;
    }

    @Override
    public Meter.Id map(Meter.Id id) {
        if (id.getName().equals("app.dependency.requests")) {
            return replaceTags(
                    id,
                    Tags.of("dependency", "jwks-api", "operation", "jwks.fetch")
            );
        }

        if (id.getName().equals("app.dependency.duration")) {
            String outcome = "success".equals(id.getTag("outcome")) ? "success" : "failure";

            return replaceTags(id, Tags.of(
                    "dependency", "jwks-api", "operation", "jwks.fetch", "outcome", outcome
            ));
        }

        if (id.getName().equals("http.client.requests")) {
            String client = id.getTag("clientName");
            String dependency = "jwks-api".equals(client)
                    || "jwks-api".equals(id.getTag("dependency"))
                    ? "jwks-api"
                    : "other";
            String statusClass = id.getTag("status_class");

            if (!Set.of("1xx", "2xx", "3xx", "4xx", "5xx", "other").contains(
                    statusClass == null ? "" : statusClass
            )) {
                String status = status(id);

                statusClass = "OTHER".equals(status) ? "other" : status.charAt(0) + "xx";
            }

            return replaceTags(
                    id,
                    Tags.of("dependency", dependency, "status_class", statusClass)
            );
        }

        if (databaseMetric(id.getName())) {
            return replaceTags(id, Tags.of("pool", "default"));
        }

        if (id.getName().equals("app.security.events")) {
            String operation = "authorization".equals(id.getTag("operation"))
                    ? "authorization" : "authentication";
            String candidate = id.getTag("outcome");
            String outcome = candidate != null && Set.of(
                    "success", "unauthenticated", "denied", "invalid_request", "failure"
            ).contains(candidate) ? candidate : "failure";

            return replaceTags(
                    id,
                    Tags.of("operation", operation, "outcome", outcome)
            );
        }

        if (id.getName().equals("app.database.failures")) {
            return replaceTags(
                    id,
                    Tags.of("dependency", "mysql", "category", "database")
            );
        }

        if (!id.getName().equals("http.server.requests")) {
            return id;
        }

        String method = id.getTag("method");
        String source = id.getTag("uri");

        if (source == null) {
            source = id.getTag("route_family");
        }

        if (source == null) {
            source = id.getTag("route");
        }

        String matched = route(source);
        // Replace the entire HTTP tag set, including unknown routes and early 401s.
        Tags tags = Tags.of(
                "method", method != null && METHODS.contains(method) ? method : "OTHER",
                "route", legacyRoute(matched),
                "route_family", canonicalFamily(matched),
                "status", status(id)
        );

        return replaceTags(id, tags);
    }

    private static String route(String uri) {
        if (uri == null) {
            return "other";
        }

        if (uri.equals("flashcard")) {
            return "flashcards";
        }

        if (uri.equals("jlpt_levels")) {
            return "jlpt-levels";
        }

        if (uri.equals("vocabulary")) {
            return "import";
        }

        if (Set.of(
                "flashcards", "jlpt-levels", "lessons", "import", "other",
                "quiz", "quiz_admin", "vocabulary_admin", "grammar"
        ).contains(uri)
        ) {
            return uri; // Composite and Prometheus registries can both apply the filter.
        }

        if (uri.startsWith("/p04/")) {
            return route(uri.substring(5));
        }

        if (uri.startsWith("/api/v1/admin/kanji-quiz")) {
            return "quiz_admin";
        }

        if (uri.startsWith("/api/v1/admin/vocabularies")) {
            return "vocabulary_admin";
        }

        if (uri.startsWith("/api/v1/kanji-quiz")) {
            return "quiz";
        }

        if (uri.matches("/api/v1/lessons/(content/import|[^/]+/content)(/.*)?")) {
            return "grammar";
        }

        for (String route : new String[]{"flashcards", "jlpt-levels", "lessons"}) {
            String prefix = "/api/v1/" + route;

            if (uri.equals(prefix) || uri.startsWith(prefix + "/")) {
                return route;
            }
        }

        return uri.equals("/api/vocabularies/import") ? "import" : "other";
    }

    private static String legacyRoute(String matched) {
        return switch (matched) {
            case "grammar" -> "lessons";
            case "quiz", "quiz_admin", "vocabulary_admin" -> "other";
            default -> matched;
        };
    }

    private static String canonicalFamily(String matched) {
        return switch (matched) {
            case "flashcards" -> "flashcard";
            case "jlpt-levels" -> "jlpt_levels";
            case "import" -> "vocabulary";
            default -> matched;
        };
    }

    private static String status(Meter.Id id) {
        String status = id.getTag("status");

        return status != null && status.matches("[1-5][0-9][0-9]")
                ? status
                : "OTHER";
    }

    private static Meter.Id replaceTags(Meter.Id id, Tags tags) {
        return new Meter.Id(
                id.getName(),
                tags,
                id.getBaseUnit(),
                id.getDescription(),
                id.getType()
        );
    }

    private static boolean databaseMetric(String name) {
        return Set.of("sql.pool.active", "sql.pool.idle", "sql.pool.ratio",
                "sql.pool.queue.size", "sql.pool.queue.delay", "sql.pool.usage",
                "sql.pool.completed", "sql.pool.rejected", "mysql.processing",
                "mysql.queue.delay", "mysql.queue.size", "mysql.current", "mysql.completed",
                "mysql.reset"
        ).contains(name);
    }

    @Override
    public DistributionStatisticConfig configure(
            Meter.Id id,
            DistributionStatisticConfig config
    ) {
        if (id.getName().equals("http.server.requests")
                || id.getName().equals("http.client.requests")
        ) {
            // Micrometer Timer boundaries are nanoseconds; export bounded Prometheus buckets.
            return DistributionStatisticConfig.builder()
                    .serviceLevelObjectives(
                            10e6, 50e6, 100e6, 250e6, 500e6,
                            1e9, 2e9, 2.5e9, 5e9, 10e9
                    )
                    .build()
                    .merge(config);
        }

        return config;
    }
}
