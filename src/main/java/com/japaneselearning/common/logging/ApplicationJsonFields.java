package com.japaneselearning.common.logging;

import io.quarkus.logging.json.runtime.JsonFormatter.JsonLogGenerator;
import io.quarkus.logging.json.runtime.JsonProvider;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logmanager.ExtLogRecord;

import java.util.Set;
import java.util.UUID;

// The official formatter owns escaping and NDJSON framing. Only allowlisted MDC is exported.
public class ApplicationJsonFields implements JsonProvider {
    private static final String INSTANCE = UUID.randomUUID().toString();

    private static final Set<String> TEXT =
            Set.of(
                    "event.name",
                    "traceId",
                    "spanId",
                    "correlationId",
                    "legacyTraceId",
                    "http.request.method",
                    "http.route",
                    "learning.status",
                    "learning.previous_status",
                    "outcome",
                    "error.type",
                    "error.code",
                    "error.stack_trace",
                    "operation",
                    "reason",
                    "caller.service.claimed",
                    "parentSpanId",
                    "network.protocol.version",
                    "url.scheme",
                    "handler.class",
                    "handler.method",
                    "operation.class",
                    "operation.method"
            );

    private static final Set<String> NUMBERS =
            Set.of(
                    "page",
                    "size",
                    "intervalSeconds",
                    "repetitions",
                    "http.response.status_code",
                    "durationMs",
                    "total",
                    "count",
                    "created",
                    "updated",
                    "succeeded",
                    "failed",
                    "lapses",
                    "dueCount",
                    "studiedCount",
                    "masteredCount",
                    "studiedBasisPoints",
                    "masteryBasisPoints",
                    "http.response.body.size",
                    "slowThresholdMs",
                    "schemaVersion",
                    "contentVersion",
                    "skipped"
            );

    private static final Set<String> FLAGS =
            Set.of("isSlow", "http.response.started", "security.authenticated");

    @Override
    public void writeTo(JsonLogGenerator generator, ExtLogRecord record) throws Exception {
        generator.add("log.schema.version", 1);
        canonical(generator, record);

        for (String key : TEXT) {
            String value = record.getMdc(key);

            if (value != null && validTraceField(key, value)) {
                generator.add(key, value);
            }
        }

        for (String key : NUMBERS) {
            String value = record.getMdc(key);

            if (value != null) {
                try {
                    generator.add(key, Long.parseLong(value));
                } catch (NumberFormatException ignored) {
                    // Omit malformed fields, never lose the record or change its numeric schema.
                }
            }
        }

        for (String key : FLAGS) {
            String value = record.getMdc(key);
            if ("true".equals(value) || "false".equals(value)) {
                // Quarkus 3.38.2's boolean SPI writes strings; keep that schema explicit.
                generator.add(key, value);
            }
        }
    }

    private static boolean validTraceField(String key, String value) {
        if (key.equals("traceId")) {
            return validHex(value, 32);
        }

        if (key.equals("spanId") || key.equals("parentSpanId")) {
            return validHex(value, 16);
        }

        return true;
    }

    private static boolean validHex(String value, int length) {
        return value != null
                && value.length() == length
                && !value.equals("0".repeat(length))
                && value.matches("[0-9a-f]+");
    }

    private static String metadata(String key, String fallback) {
        String value =
                ConfigProvider.getConfig()
                        .getOptionalValue(key, String.class)
                        .orElse(fallback);

        return value.length() <= 128 && value.matches("[A-Za-z0-9._-]+") ? value : "unknown";
    }

    private static void copy(
            JsonLogGenerator generator,
            ExtLogRecord record,
            String target,
            String source
    ) throws Exception {
        String value = record.getMdc(source);

        if (value != null && !value.isEmpty()) {
            generator.add(target, value);
        }
    }

    private static void canonical(
            JsonLogGenerator generator,
            ExtLogRecord record
    ) throws Exception {
        // Keep native outcome compatible. A validated reader assigns canonical schema_version.
        generator.add("service_name", "vocabulary-api");
        generator.add("service_version", metadata("APP_VERSION", "unknown"));
        generator.add("service_instance_id", metadata("APP_INSTANCE_ID", INSTANCE));
        String environment = metadata("application.logging.environment", "unknown");
        generator.add(
                "environment",
                Set.of("development", "test", "staging", "production").contains(environment)
                        ? environment
                        : "unknown"
        );

        String event = record.getMdc("event.name");
        generator.add(
                "event_name",
                event == null
                        ? "log.message"
                        : event.equals("http.request.failed") ? "http.request.completed" : event
        );

        String correlation = record.getMdc("correlationId");

        if (correlation != null && correlation.matches("[A-Za-z0-9_-]{1,64}")) {
            generator.add("correlation_id", correlation);
        }

        copy(generator, record, "legacy_trace_id", "legacyTraceId");
        copy(generator, record, "error_type", "error.type");
        copy(generator, record, "error_code", "error.code");

        if (validHex(record.getMdc("traceId"), 32) && validHex(record.getMdc("spanId"), 16)) {
            copy(generator, record, "trace_id", "traceId");
            copy(generator, record, "span_id", "spanId");

            if (validHex(record.getMdc("parentSpanId"), 16)) {
                copy(generator, record, "parent_span_id", "parentSpanId");
            }
        }

        copy(generator, record, "http_method", "http.request.method");

        String route = record.getMdc("http.route");

        if (route != null) {
            generator.add("http_route", route.endsWith("/*") ? "/unmatched" : route);
            generator.add("route_family", routeFamily(route));
        }

        long status = number(record.getMdc("http.response.status_code"));

        if (status >= 100 && status <= 599) {
            generator.add("http_status", status);
        }

        long duration = number(record.getMdc("durationMs"));

        if (duration >= 0) {
            generator.add("duration_ms", duration);
        }

        String outcome = record.getMdc("outcome");

        if (record.getMdc("error.type") != null || status >= 400
                || "cancelled".equals(outcome) || "timeout".equals(outcome)
        ) {
            if (record.getMdc("error.type") == null) {
                generator.add("error_type", "http");
            }

            generator.add("error_category", errorCategory(record, status, outcome));
        }
    }

    private static String errorCategory(ExtLogRecord record, long status, String outcome) {
        if ("cancelled".equals(outcome)) {
            return "cancellation";
        }

        if ("timeout".equals(outcome)) {
            return "timeout";
        }

        if (status == 401) {
            return "authentication";
        }

        if (status == 403) {
            return "authorization";
        }

        if (status == 400) {
            return "validation";
        }

        String type = record.getMdc("error.type");
        if ("transport".equals(type)) {
            return "transport";
        }

        if (type != null && (type.contains("SqlException") || type.contains("SQLException"))) {
            return "database";
        }

        return status >= 500 ? "internal" : "unknown";
    }

    private static long number(String value) {
        try {
            return value == null ? -1 : Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }

    static String routeFamily(String route) {
        if (route.startsWith("/api/v1/admin/kanji-quiz")) {
            return "quiz_admin";
        }

        if (route.startsWith("/api/v1/admin/vocabularies")) {
            return "vocabulary_admin";
        }

        if (route.startsWith("/api/v1/kanji-quiz")) {
            return "quiz";
        }

        if (route.startsWith("/api/v1/flashcards")) {
            return "flashcard";
        }

        if (route.startsWith("/api/v1/lessons") && route.contains("/content")) {
            return "grammar";
        }

        if (route.startsWith("/api/v1/lessons")) {
            return "lessons";
        }

        if (route.startsWith("/api/v1/jlpt-levels")) {
            return "jlpt_levels";
        }

        return route.startsWith("/api/vocabularies") ? "vocabulary" : "other";
    }
}
