package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Timer;
import io.micrometer.prometheus.PrometheusConfig;
import io.micrometer.prometheus.PrometheusMeterRegistry;
import io.quarkus.micrometer.runtime.binder.vertx.VertxMeterBinderAdapter;
import io.vertx.core.spi.metrics.PoolMetrics;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoolMetricsTest {
    @Test
    void nativePoolLifecycleExportsBoundedGaugesTimersAndRejections() throws Exception {
        var registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);

        registry.config().meterFilter(new ApplicationMetricsFilter());

        Metrics.addRegistry(registry);
        try {
            var adapter = new VertxMeterBinderAdapter();

            PoolMetrics<?> pool = adapter.createPoolMetrics("sql", "secret-sentinel", 20);
            exercise(pool);

            new DatabaseFailureMetrics(registry).failed(new SQLException("secret-sentinel"));

            Timer.builder("http.client.requests")
                    .tags("clientName", "jwks-api", "uri", "/secret-sentinel", "status", "503")
                    .register(registry)
                    .record(1, TimeUnit.MILLISECONDS);
            String exposition = registry.scrape();

            assertTrue(exposition.contains("sql_pool_active"));
            assertTrue(exposition.contains("sql_pool_usage_seconds_count"));
            assertTrue(exposition.contains("sql_pool_rejected_total"));
            assertTrue(exposition.contains("app_database_failures_total"));
            assertTrue(exposition.contains("http_client_requests_seconds_count"));
            assertFalse(exposition.contains("secret-sentinel"));
            assertEquals(0, registry.get("sql.pool.active").gauge().value());

            Files.writeString(Path.of("target/p04-mysql-spi.prom"), exposition);
        } finally {
            Metrics.removeRegistry(registry);
            registry.close();
        }
    }

    private static <T> void exercise(PoolMetrics<T> pool) {
        T queued = pool.submitted();
        T active = pool.begin(queued);

        pool.end(active, true);
        pool.rejected(pool.submitted());
    }
}
