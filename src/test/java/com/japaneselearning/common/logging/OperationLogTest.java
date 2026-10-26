package com.japaneselearning.common.logging;

import com.japaneselearning.common.exception.ConflictException;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import jakarta.interceptor.InvocationContext;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;

class OperationLogTest {
    @LogOperation(value = "test.write", id = "sessionId")
    public Uni<Object> write(String subject, Long id, Object payload) {
        throw new UnsupportedOperationException();
    }

    @Test
    void observesCommitAndPreservesFailuresWithoutPayloadOrDuplicateStackLogs() throws Exception {
        var logger = Logger.getLogger(OperationLog.class.getName());
        List<ExtLogRecord> records = new ArrayList<>();
        var handler = new Handler() {
            public void publish(LogRecord record) {
                var extended = (ExtLogRecord) record;
                extended.copyMdc();
                records.add(extended);
            }

            public void flush() {
            }

            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            AtomicReference<UniEmitter<? super Object>> commit = new AtomicReference<>();
            Uni<Object> pending = Uni.createFrom().<Object>emitter(commit::set);
            var interceptor = new OperationLog();
            interceptor.slowOperationMs = Long.MAX_VALUE;
            Object result = new Object();
            AtomicReference<Object> actual = new AtomicReference<>();
            observe(interceptor, pending).subscribe().with(actual::set);
            assertTrue(records.isEmpty());
            commit.get().complete(result);
            assertSame(result, actual.get());
            assertEquals(1, records.size());
            assertEquals("test.write.completed", records.get(0).getMdc("event.name"));
            assertEquals("42", records.get(0).getMdc("sessionId"));
            assertNull(records.get(0).getMdc("subject"));
            assertNotNull(records.get(0).getMdc("durationMs"));
            records.clear();

            var conflict = new ConflictException("TEST_CONFLICT", "credential-sentinel");
            assertSame(conflict, assertThrows(ConflictException.class,
                    () -> observe(interceptor, Uni.createFrom().failure(conflict)).await().indefinitely()));
            assertEquals(1, records.size());
            assertEquals("test.write.rejected", records.get(0).getMdc("event.name"));
            assertEquals("TEST_CONFLICT", records.get(0).getMdc("error.code"));
            assertNull(records.get(0).getThrown());
            records.clear();

            var failure = new IllegalStateException("credential-sentinel");
            assertSame(failure, assertThrows(IllegalStateException.class,
                    () -> observe(interceptor, Uni.createFrom().failure(failure)).await().indefinitely()));
            assertTrue(records.isEmpty(), "Unexpected diagnostic belongs to central exception handling");

            interceptor.slowOperationMs = 0;
            observe(interceptor, OperationLog.outcome(Uni.createFrom().item(result), "replayed")).await().indefinitely();
            assertEquals(2, records.size());
            assertEquals("test.write.replayed", records.get(0).getMdc("event.name"));
            assertEquals("test.write.slow", records.get(1).getMdc("event.name"));
        } finally {
            logger.removeHandler(handler);
            org.jboss.logmanager.MDC.clear();
        }
    }

    private Uni<?> observe(OperationLog interceptor, Uni<?> result) {
        try {
            var method = getClass().getMethod("write", String.class, Long.class, Object.class);
            Object payload = new Object() {
                public String toString() {
                    throw new AssertionError("Never stringify payloads");
                }
            };
            var invocation = (InvocationContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{InvocationContext.class}, (proxy, invoked, arguments) -> switch (invoked.getName()) {
                        case "getMethod" -> method;
                        case "getParameters" -> new Object[]{"private-subject", 42L, payload};
                        case "proceed" -> result;
                        default -> throw new UnsupportedOperationException(invoked.getName());
                    });
            return (Uni<?>) interceptor.log(invocation);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
