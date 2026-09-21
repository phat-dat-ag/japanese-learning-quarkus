package com.japaneselearning.common.web;

import io.quarkus.vertx.http.runtime.filters.Filters;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;
import org.jboss.logmanager.MDC;

import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@ApplicationScoped
public class CorrelationIdFilter {

    public static final String HEADER = "X-Correlation-ID";
    public static final String CONTEXT_KEY = CorrelationIdFilter.class.getName();
    private static final Pattern VALID_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");
    private static final Logger LOG = Logger.getLogger(CorrelationIdFilter.class);

    void register(@Observes Filters filters) {
        // Run before proactive authentication, including rejected bearer requests.
        filters.register(this::filter, Integer.MAX_VALUE);
    }

    private void filter(RoutingContext context) {
        String id = getOrGenerateId(context.request().headers().getAll(HEADER));
        context.put(CONTEXT_KEY, id);
        // Quarkus MDC is isolated in the request's duplicated Vert.x context.
        MDC.put("correlationId", id);
        long started = System.nanoTime();
        context.addHeadersEndHandler(ignored -> context.response().putHeader(HEADER, id));
        context.addEndHandler(ignored -> {
            try {
                LOG.infof("HTTP request completed with status %d in %dms correlationId=%s",
                        context.response().getStatusCode(), (System.nanoTime() - started) / 1_000_000, id);
            } finally {
                MDC.remove("correlationId");
            }
        });
        context.next();
    }

    static String getOrGenerateId(List<String> values) {
        if (values.size() == 1) {
            String value = values.get(0);
            if (value != null && value.length() <= 64 && VALID_ID.matcher(value).matches()) {
                return value;
            }
        }
        return UUID.randomUUID().toString().replace("-", "");
    }
}
