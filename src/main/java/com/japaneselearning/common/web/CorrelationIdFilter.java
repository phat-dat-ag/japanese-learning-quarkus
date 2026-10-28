package com.japaneselearning.common.web;

import com.japaneselearning.common.exception.handler.HttpErrors;
import com.japaneselearning.common.logging.StructuredLog;
import io.opentelemetry.api.trace.Span;
import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;
import org.jboss.logmanager.MDC;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class CorrelationIdFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String CONTEXT_KEY = CorrelationIdFilter.class.getName();
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Logger LOG = Logger.getLogger(CorrelationIdFilter.class);

    public static final String TRACE_KEY = CONTEXT_KEY + ".trace";
    public static final String RESPONSE_TRACE_KEY = CONTEXT_KEY + ".responseTrace";
    public static final String DISTRIBUTED_TRACE_HEADER = "X-Distributed-Trace-Id";
    public static final String ROUTE_KEY = CONTEXT_KEY + ".route";
    public static final String ERROR_CODE_KEY = CONTEXT_KEY + ".errorCode";
    public static final String ERROR_TYPE_KEY = CONTEXT_KEY + ".errorType";
    private static final Set<String> METHODS = Set.of("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD", "OPTIONS");

    @ConfigProperty(name = "application.logging.slow-operation-ms", defaultValue = "1000")
    long slowOperationMs;

    void register(@Observes Filters filters) {
        // Run before proactive authentication, including rejected bearer requests.
        filters.register(this::filter, Integer.MAX_VALUE);
    }

    private void filter(RoutingContext context) {
        String id = getOrGenerateId(context.request().headers().getAll(HEADER));
        context.put(CONTEXT_KEY, id);
        // Quarkus MDC is isolated in the request's duplicated Vert.x context.
        MDC.put("correlationId", id);

        var span = Span.current().getSpanContext();
        String traceId = span.getTraceId();
        String spanId = span.getSpanId();

        context.put(TRACE_KEY, traceId);

        MDC.put("traceId", traceId);
        MDC.put("spanId", spanId);

        String legacyTraceId = validatedId(
                context.request().headers().getAll(RequestTraceFilter.TRACE_ID_HEADER)
        );

        if (legacyTraceId != null) {
            MDC.put("legacyTraceId", legacyTraceId);
        }

        long started = System.nanoTime();
        String method = context.request().method().name();

        Map<String, Object> fields = new HashMap<>();

        fields.put("correlationId", id);
        fields.put("traceId", traceId);
        fields.put("spanId", spanId);

        if (legacyTraceId != null) {
            fields.put("legacyTraceId", legacyTraceId);
        }

        fields.put("http.request.method", METHODS.contains(method) ? method : "OTHER");
        fields.put("http.route", safeRoute(context.normalizedPath()));

        boolean applicationRequest = !context.normalizedPath().startsWith("/q/");

        if (applicationRequest) {
            StructuredLog.event(
                    LOG,
                    Logger.Level.INFO,
                    "http.request.started",
                    "Started "
                            + fields.get("http.request.method")
                            + " " + fields.get("http.route"),
                    fields
            );
        }
        context.addHeadersEndHandler(ignored -> {
            context.response().putHeader(HEADER, id);

            String responseTrace = context.get(RESPONSE_TRACE_KEY);

            context.response().putHeader(
                    RequestTraceFilter.TRACE_ID_HEADER,
                    responseTrace == null ? traceId : responseTrace
            );
            context.response().putHeader(DISTRIBUTED_TRACE_HEADER, traceId);
        });

        context.addEndHandler(ignored -> {
            try {
                if (applicationRequest) {
                    int status = context.response().getStatusCode();
                    long duration = (System.nanoTime() - started) / 1_000_000;

                    fields.put("durationMs", duration);
                    fields.put("http.response.status_code", status);

                    String route = context.get(ROUTE_KEY);

                    if (route != null) {
                        fields.put("http.route", route);
                    }

                    boolean failed = status >= 400 || ignored.failed();

                    fields.put("outcome", failed ? "failure" : "success");

                    if (failed) {
                        String code = context.get(ERROR_CODE_KEY);
                        String type = context.get(ERROR_TYPE_KEY);
                        fields.put("error.code", code != null
                                ? code
                                : ignored.failed() && status < 400
                                ? "REQUEST_INTERRUPTED"
                                : HttpErrors.forStatus(status).code()
                        );
                        fields.put("error.type", type != null
                                ? type
                                : ignored.failed()
                                ? "transport"
                                : status == 401
                                ? "authentication"
                                : status == 403
                                ? "authorization"
                                : "http"
                        );
                    }

                    StructuredLog.event(LOG, status >= 500
                                    ? Logger.Level.ERROR
                                    : failed || duration >= slowOperationMs
                                    ? Logger.Level.WARN
                                    : Logger.Level.INFO,
                            failed ? "http.request.failed" : "http.request.completed",
                            terminalMessage(fields, status, duration, ignored.failed()), fields);

                    if (duration >= slowOperationMs) {
                        StructuredLog.event(
                                LOG,
                                Logger.Level.WARN,
                                "http.request.slow",
                                "Request exceeded the configured duration threshold: "
                                        + fields.get("http.request.method")
                                        + " " + fields.get("http.route")
                                        + " in " + duration + " ms", fields
                        );
                    }
                }
            } finally {
                MDC.remove("correlationId");
                MDC.remove("traceId");
                MDC.remove("spanId");
                MDC.remove("legacyTraceId");
                MDC.remove("operation");
            }
        });
        context.next();
    }

    static String terminalMessage(Map<String, Object> fields, int status, long duration, boolean interrupted) {
        String action = interrupted ? "Interrupted" : status >= 500 ? "Failed" : status >= 400 ? "Rejected" : "Completed";
        String reason = interrupted ? " before the response completed"
                : status >= 500 ? " because of an unexpected internal error"
                : status == 404 ? " because the referenced resource was not found"
                : status == 409 ? " because the request conflicts with the current state"
                : status == 400 ? " due to a request or business validation failure" : "";
        return action + " " + fields.get("http.request.method") + " " + fields.get("http.route")
                + " with status " + status + " in " + duration + " ms" + reason;
    }

    private static String safeRoute(String path) {
        // Before REST matching, never log arbitrary path segments or query strings.
        for (String prefix : List.of("/api/v1/admin/vocabularies", "/api/v1/admin/kanji-quiz",
                "/api/v1/kanji-quiz", "/api/v1/flashcards", "/api/v1/lessons", "/api/v1/jlpt-levels",
                "/api/vocabularies")) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return prefix + "/*";
            }
        }
        return "/unmatched";
    }

    static String getOrGenerateId(List<String> values) {
        String id = validatedId(values);
        return id != null ? id : UUID.randomUUID().toString().replace("-", "");
    }

    static String validatedId(List<String> values) {
        if (values.size() == 1) {
            String value = values.get(0);
            if (value != null && value.length() <= 64 && VALID_ID.matcher(value).matches()) {
                return value;
            }
        }
        return null;
    }
}
