package com.japaneselearning.common.logging;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.quiz.admin.dto.QuizImportError;
import com.japaneselearning.quiz.admin.service.QuizImportItem;
import com.japaneselearning.quiz.admin.service.QuizImportService;
import io.smallrye.mutiny.Uni;
import jakarta.interceptor.InvocationContext;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class OperationLogBehaviorTest {
    private final Logger logger = Logger.getLogger(OperationLog.class.getName());
    private final List<ExtLogRecord> records = new ArrayList<>();
    private java.util.logging.Level previousLevel;
    private final Handler handler = new Handler() {
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

    @LogOperation("test.sync")
    public Object sync() {
        return null;
    }

    @LogOperation("test.reactive")
    public Uni<?> reactive() {
        return null;
    }

    @LogOperation("test.class")
    public static class ClassOperation {
        public String execute() {
            return "ok";
        }
    }

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        logger.setLevel(java.util.logging.Level.FINE);
        logger.addHandler(handler);
        MDC.put("operation", "outer");
        MDC.put("correlationId", "request-42");
        MDC.put("traceId", "0123456789abcdef0123456789abcdef");
    }

    @AfterEach
    void release() {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
        MDC.clear();
    }

    @Test
    void synchronousAndClassBindingsPreserveValuesAndFailures() throws Exception {
        assertEquals("ok", observe(getClass().getMethod("sync"), this, "ok", null));
        assertEquals(List.of("test.sync.started", "test.sync.completed"), events());
        assertEquals("outer", MDC.get("operation"));
        records.clear();
        var conflict = new ConflictException("TEST_CONFLICT", "credential-sentinel");
        assertSame(conflict, assertThrows(ConflictException.class,
                () -> observe(getClass().getMethod("sync"), this, null, conflict)));
        assertEquals("test.sync.rejected", events().get(1));
        assertEquals(org.jboss.logmanager.Level.WARN, records.get(1).getLevel());
        assertNotNull(records.get(1).getMdc("durationMs"));
        assertNull(records.get(1).getThrown());
        records.clear();
        var target = new ClassOperation();
        assertEquals("ok", observe(ClassOperation.class.getMethod("execute"), target, "ok", null));
        assertEquals("test.class.completed", events().get(1));
    }

    @Test
    void subscriptionsAreIsolatedAndCancellationAndFailuresRestoreMdc() throws Exception {
        AtomicInteger subscriptions = new AtomicInteger();
        Uni<?> result = Uni.createFrom().deferred(() -> subscriptions.incrementAndGet() == 1
                ? OperationLog.outcome(Uni.createFrom().item("ok"), "accepted", Map.of("sessionId", 42L))
                : Uni.createFrom().item("ok"));
        Uni<?> observed = (Uni<?>) observe(getClass().getMethod("reactive"), this, result, null);
        assertTrue(records.isEmpty());
        observed.await().indefinitely();
        observed.await().indefinitely();
        assertEquals(List.of("test.reactive.started", "test.reactive.accepted",
                "test.reactive.started", "test.reactive.completed"), events());
        assertNull(records.get(3).getMdc("sessionId"));
        assertEquals("outer", MDC.get("operation"));
        assertTrue(records.stream().allMatch(record -> "request-42".equals(record.getMdc("correlationId"))));
        records.clear();
        var failure = new IllegalStateException("credential-sentinel");
        var failed = (Uni<?>) observe(getClass().getMethod("reactive"), this, Uni.createFrom().failure(failure), null);
        assertSame(failure, assertThrows(IllegalStateException.class, () -> failed.await().indefinitely()));
        assertEquals(List.of("test.reactive.started", "test.reactive.failed"), events());
        assertNull(records.get(1).getThrown());
        assertEquals("outer", MDC.get("operation"));
        records.clear();
        var pending = (Uni<?>) observe(getClass().getMethod("reactive"), this, Uni.createFrom().nothing(), null);
        pending.subscribe().with(ignored -> fail()).cancel();
        assertEquals(List.of("test.reactive.started", "test.reactive.cancelled"), events());
        assertEquals("outer", MDC.get("operation"));
    }

    @Test
    void actualPartialQuizImportHasOneWarnSummaryWithoutItemPayloads() throws Exception {
        var service = new QuizImportService(null);
        var item = new QuizImportItem(null, List.of(new QuizImportError("secret-field", "INVALID", "secret-value")));
        var method = QuizImportService.class.getMethod("importQuestions", List.class);
        var result = (Uni<?>) observe(method, service, service.importQuestions(List.of(item)), null);
        result.await().indefinitely();
        assertEquals(List.of("quiz.question.import.started", "quiz.question.import.partial_failure"), events());
        var summary = records.get(1);
        assertEquals(org.jboss.logmanager.Level.WARN, summary.getLevel());
        assertEquals("1", summary.getMdc("failed"));
        assertEquals("0", summary.getMdc("succeeded"));
        assertEquals("partial_failure", summary.getMdc("outcome"));
        assertFalse(summary.getFormattedMessage().contains("secret"));
    }

    private List<String> events() {
        return records.stream().map(record -> record.getMdc("event.name")).toList();
    }

    private Object observe(Method method, Object target, Object result, Exception failure) throws Exception {
        var invocation = (InvocationContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{InvocationContext.class}, (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getTarget" -> target;
                    case "getParameters" -> new Object[method.getParameterCount()];
                    case "proceed" -> {
                        if (failure != null) throw failure;
                        yield result;
                    }
                    default -> throw new UnsupportedOperationException(invoked.getName());
                });
        var interceptor = new OperationLog();
        interceptor.slowOperationMs = Long.MAX_VALUE;
        return interceptor.log(invocation);
    }
}
