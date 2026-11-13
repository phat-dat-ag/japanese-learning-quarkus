package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.config.MeterFilterReply;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ApplicationMetricsFilterTest {
    @Test
    void collapsesTenThousandArbitraryLabelsBeforeRegistration() {
        var filter = new ApplicationMetricsFilter();
        var results = new HashSet<Meter.Id>();
        var registry = new SimpleMeterRegistry();

        try {
            registry.config().meterFilter(filter);

            for (int index = 0; index < 10000; index++) {
                Tags tags = Tags.of("method", "SECRET" + index,
                        "uri", "/unknown/" + index, "status", "401",
                        "user_id", "secret" + index, "trace_id", "secret" + index
                );

                results.add(filter.map(id("http.server.requests", tags)));

                Timer.builder("http.server.requests")
                        .tags(tags)
                        .register(registry)
                        .record(1, TimeUnit.MILLISECONDS);
            }

            assertEquals(1, results.size());
            assertEquals(1, registry.getMeters().size());
            assertEquals(10000, registry.get("http.server.requests").timer().count());
            assertEquals(4, results.iterator().next().getTags().size());
        } finally {
            registry.close();
        }
    }

    @Test
    void keepsLegacyAliasesAndAddsBoundedQuizAdminAndGrammarRoutes() {
        var filter = new ApplicationMetricsFilter();

        Map<String, String> cases = Map.of(
                "/api/v1/flashcards/{id}", "flashcards",
                "/api/v1/jlpt-levels", "jlpt-levels",
                "/api/v1/lessons", "lessons",
                "/api/vocabularies/import", "import",
                "/api/v1/kanji-quiz/history/123", "quiz",
                "/api/v1/admin/kanji-quiz/questions/123", "quiz_admin",
                "/api/v1/admin/vocabularies/123", "vocabulary_admin",
                "/api/v1/lessons/123/content", "grammar",
                "/api/v1/lessons/content/import", "grammar",
                "/p04/grammar", "grammar"
        );

        cases.forEach((uri, expected) -> {
            Meter.Id mapped = filter.map(id("http.server.requests", Tags.of(
                    "method", "GET", "uri", uri, "status", "200"
            )));

            String legacy = switch (expected) {
                case "grammar" -> "lessons";
                case "quiz", "quiz_admin", "vocabulary_admin" -> "other";
                default -> expected;
            };

            assertEquals(legacy, mapped.getTag("route"));

            String family = switch (expected) {
                case "flashcards" -> "flashcard";
                case "jlpt-levels" -> "jlpt_levels";
                case "import" -> "vocabulary";
                default -> expected;
            };

            assertEquals(family, mapped.getTag("route_family"));
            assertEquals(mapped, filter.map(mapped));
        });
    }

    @Test
    void stripsOutboundUrlsSqlPoolIdentityAndUnapprovedMeters() {
        var filter = new ApplicationMetricsFilter();

        Meter.Id client = filter.map(id("http.client.requests", Tags.of(
                "uri", "/token/secret", "clientName", "jwks-api", "status", "503"
        )));

        assertEquals("jwks-api", client.getTag("dependency"));
        assertEquals(client, filter.map(client));
        assertEquals(2, client.getTags().size());
        assertEquals(MeterFilterReply.NEUTRAL, filter.accept(client));
        Meter.Id pool = filter.map(id("sql.pool.active", Tags.of(
                "pool.name", "secret", "connectionString", "secret"
        )));

        assertEquals(Tags.of("pool", "default"), Tags.of(pool.getTags()));
        assertEquals(MeterFilterReply.DENY, filter.accept(id("unapproved", Tags.empty())));
    }

    @Test
    void countsSecurityAndWrappedDatabaseFailuresWithoutSensitiveValues() {
        var registry = new SimpleMeterRegistry();

        try {
            registry.config().meterFilter(new ApplicationMetricsFilter());

            var security = new SecurityMetrics(registry);

            security.completed(200, true);
            security.completed(401, false);
            security.completed(403, true);
            security.completed(200, false);

            var database = new DatabaseFailureMetrics(registry);

            database.failed(new IllegalStateException("secret",
                    new SQLException("secret SQL and credentials")
            ));
            database.failed(new IllegalArgumentException("secret"));

            assertEquals(4, registry.getMeters().size());
            assertEquals(1, registry.get("app.database.failures").counter().count());
            assertEquals(3, registry.find("app.security.events").counters().size());
            assertEquals(1, registry.get("app.security.events")
                    .tag("outcome", "unauthenticated").counter().count());
            assertEquals(1, registry.get("app.security.events")
                    .tag("outcome", "denied").counter().count());
            for (Meter meter : registry.getMeters()) {
                assertFalse(meter.getId().toString().contains("secret"));
                assertTrue(meter.getId().getTags().size() <= 2);
            }
        } finally {
            registry.close();
        }
    }

    private static Meter.Id id(String name, Tags tags) {
        return new Meter.Id(name, tags, "seconds", "fixture", Meter.Type.TIMER);
    }
}
