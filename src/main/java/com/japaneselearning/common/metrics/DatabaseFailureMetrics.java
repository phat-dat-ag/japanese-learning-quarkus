package com.japaneselearning.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;

import jakarta.enterprise.context.ApplicationScoped;

import java.sql.SQLException;

@ApplicationScoped
public class DatabaseFailureMetrics {
    private final MeterRegistry registry;

    public DatabaseFailureMetrics(MeterRegistry registry) {
        this.registry = registry;
    }

    public void failed(Throwable failure) {
        Throwable cause = failure;

        for (int depth = 0; cause != null && depth < 8; depth++) {
            // Classify by type only. Never inspect SQL, bind values or exception messages.
            String type = cause.getClass().getName();

            if (type.startsWith("io.vertx.mysqlclient.")
                    || type.startsWith("org.hibernate.exception.")
                    || cause instanceof SQLException
            ) {
                registry.counter(
                        "app.database.failures",
                        "dependency", "mysql", "category", "database"
                ).increment();

                return;
            }

            cause = cause.getCause();
        }
    }
}
