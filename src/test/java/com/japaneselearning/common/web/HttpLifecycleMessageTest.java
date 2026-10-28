package com.japaneselearning.common.web;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpLifecycleMessageTest {
    @Test
    void terminalMessagesUseOnlySafeFieldsAndDescribeInterruptedResponses() {
        Map<String, Object> fields = Map.of("http.request.method", "POST",
                "http.route", "/api/v1/flashcards/sessions/{id}");
        assertEquals("Completed POST /api/v1/flashcards/sessions/{id} with status 201 in 35 ms",
                CorrelationIdFilter.terminalMessage(fields, 201, 35, false));
        assertTrue(CorrelationIdFilter.terminalMessage(fields, 404, 3, false)
                .endsWith("because the referenced resource was not found"));
        assertTrue(CorrelationIdFilter.terminalMessage(fields, 400, 3, false)
                .endsWith("due to a request or business validation failure"));
        assertTrue(CorrelationIdFilter.terminalMessage(fields, 500, 3, false)
                .endsWith("because of an unexpected internal error"));
        assertEquals("Interrupted POST /api/v1/flashcards/sessions/{id} with status 200 in 4 ms before the response completed",
                CorrelationIdFilter.terminalMessage(fields, 200, 4, true));
    }

    @Test
    @SuppressWarnings("unchecked")
    void interruptedResponseEmitsOneFailureAndRestoresContext() throws Exception {
        var records = new java.util.ArrayList<org.jboss.logmanager.ExtLogRecord>();
        var logger = java.util.logging.Logger.getLogger(CorrelationIdFilter.class.getName());
        var handler = new java.util.logging.Handler() {
            public void publish(java.util.logging.LogRecord record) {
                var extended = (org.jboss.logmanager.ExtLogRecord) record;
                extended.copyMdc();
                records.add(extended);
            }

            public void flush() {
            }

            public void close() {
            }
        };
        var values = new java.util.HashMap<String, Object>();
        var end = new java.util.concurrent.atomic.AtomicReference<io.vertx.core.Handler<io.vertx.core.AsyncResult<Void>>>();
        var headers = io.vertx.core.MultiMap.caseInsensitiveMultiMap()
                .add(CorrelationIdFilter.HEADER, "interrupted-request");
        var request = proxy(io.vertx.core.http.HttpServerRequest.class, (object, method, args) -> switch (method.getName()) {
            case "headers" -> headers;
            case "method" -> io.vertx.core.http.HttpMethod.POST;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var response = proxy(io.vertx.core.http.HttpServerResponse.class, (object, method, args) -> switch (method.getName()) {
            case "getStatusCode" -> 200;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        var context = proxy(io.vertx.ext.web.RoutingContext.class, (object, method, args) -> switch (method.getName()) {
            case "request" -> request;
            case "response" -> response;
            case "normalizedPath" -> "/api/v1/flashcards/private-sentinel";
            case "put" -> {
                values.put((String) args[0], args[1]);
                yield object;
            }
            case "get" -> values.get(args[0]);
            case "addHeadersEndHandler" -> 0;
            case "addEndHandler" -> {
                end.set((io.vertx.core.Handler<io.vertx.core.AsyncResult<Void>>) args[0]);
                yield 1;
            }
            case "next" -> null;
            default -> throw new UnsupportedOperationException(method.getName());
        });
        logger.addHandler(handler);
        try {
            var filter = new CorrelationIdFilter();
            filter.slowOperationMs = Long.MAX_VALUE;
            var method = CorrelationIdFilter.class.getDeclaredMethod("filter", io.vertx.ext.web.RoutingContext.class);
            method.setAccessible(true);
            method.invoke(filter, context);
            values.put(CorrelationIdFilter.ROUTE_KEY, "/api/v1/flashcards/{id}");
            end.get().handle(io.vertx.core.Future.failedFuture("credential-sentinel"));
            assertEquals(2, records.size());
            var terminal = records.get(1);
            assertEquals("http.request.failed", terminal.getMdc("event.name"));
            assertEquals("REQUEST_INTERRUPTED", terminal.getMdc("error.code"));
            assertEquals("transport", terminal.getMdc("error.type"));
            assertEquals("failure", terminal.getMdc("outcome"));
            assertEquals("interrupted-request", terminal.getMdc("correlationId"));
            assertEquals(records.get(0).getMdc("traceId"), terminal.getMdc("traceId"));
            assertTrue(terminal.getFormattedMessage().startsWith("Interrupted POST /api/v1/flashcards/{id}"));
            assertTrue(records.stream().noneMatch(record -> record.getFormattedMessage().contains("sentinel")));
            assertNull(org.jboss.logmanager.MDC.get("correlationId"));
            assertNull(org.jboss.logmanager.MDC.get("traceId"));
        } finally {
            logger.removeHandler(handler);
            org.jboss.logmanager.MDC.clear();
        }
    }

    private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
        return type.cast(java.lang.reflect.Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler));
    }

}
