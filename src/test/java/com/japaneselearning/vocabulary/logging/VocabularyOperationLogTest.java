package com.japaneselearning.vocabulary.logging;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.vocabulary.admin.dto.VocabularyCoreResponse;
import com.japaneselearning.vocabulary.admin.dto.VocabularyCoreUpdateRequest;
import com.japaneselearning.vocabulary.admin.service.VocabularyCoreEditService;
import com.japaneselearning.vocabulary.dto.LessonBatchResponse;
import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import com.japaneselearning.vocabulary.service.LessonBatchService;
import com.japaneselearning.vocabulary.service.LessonService;
import io.smallrye.mutiny.Context;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import jakarta.annotation.Priority;
import jakarta.interceptor.InvocationContext;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

class VocabularyOperationLogTest {
    private final List<ExtLogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getLogger(VocabularyOperationLog.class.getName());
    private final Handler handler = new Handler() {
        public void publish(LogRecord record) {
            records.add((ExtLogRecord) record);
        }

        public void flush() {
        }

        public void close() {
        }
    };

    @BeforeEach
    void captureLogs() {
        logger.addHandler(handler);
    }

    @AfterEach
    void releaseLogs() {
        logger.removeHandler(handler);
    }

    @Test
    void observesTransactionCompletionWithoutLoggingPendingWrites() {
        AtomicReference<UniEmitter<? super LessonResponse>> commit = new AtomicReference<>();
        Uni<LessonResponse> transaction = Uni.createFrom().<LessonResponse>emitter(commit::set);
        AtomicReference<Object> returned = new AtomicReference<>();

        var subscription = observe(createMethod(), new Object[]{null}, transaction)
                .subscribe().with(returned::set, Assertions::fail);

        assertTrue(records.isEmpty());
        LessonResponse lesson = new LessonResponse(7L, 1, "secret-title", "secret-description");
        commit.get().complete(lesson);
        assertSame(lesson, returned.get());
        assertEquals(1, records.size());
        assertEquals("Business operation completed operation=lesson.create lessonId=7", message(0));
        assertNull(records.get(0).getThrown());
        subscription.cancel();
    }

    @Test
    void commitFailureIsUnchangedAndLeftToCentralErrorLogging() {
        var failure = new IllegalStateException("password=secret");
        var result = observe(createMethod(), new Object[]{null}, Uni.createFrom().failure(failure));

        assertSame(failure, assertThrows(IllegalStateException.class, () -> result.await().indefinitely()));
        assertTrue(records.isEmpty(), "No success or duplicate ERROR before centralized handling");
    }

    @Test
    void warnsOnConflictsWithOnlyAllowlistedIdentifiers() throws Exception {
        Method method = VocabularyCoreEditService.class.getMethod(
                "updateVocabularyCore", Long.class, VocabularyCoreUpdateRequest.class
        );

        Object payload = new Object() {
            @Override
            public String toString() {
                throw new AssertionError("Payload must never be stringified");
            }
        };

        var failure = new ConflictException("SENSITIVE_CODE", "sensitive-payload");

        Uni<?> result = observe(method, new Object[]{42L, payload}, Uni.createFrom().failure(failure));
        assertSame(failure, assertThrows(ConflictException.class, () -> result.await().indefinitely()));
        assertEquals(1, records.size());
        assertEquals("WARN", records.get(0).getLevel().getName());
        assertEquals("Business operation rejected operation=vocabulary.core.update vocabularyId=42 reason=conflict",
                message(0));
        assertNull(records.get(0).getThrown());
    }

    @Test
    void nestedCreatesProduceOneBatchSummaryAndDoNotChangeRecovery() throws Exception {
        Method batchMethod = LessonBatchService.class.getMethod("createLessons", List.class);
        Uni<?> batch = Uni.createFrom().voidItem()
                .chain(() -> observe(createMethod(), new Object[]{null},
                        Uni.createFrom().item(new LessonResponse(1L, 1, "secret", "secret"))))
                .chain(() -> observe(createMethod(), new Object[]{null},
                        Uni.createFrom().failure(new ConflictException("CONFLICT", "secret"))))
                .onFailure(ConflictException.class).recoverWithNull()
                .replaceWith(new LessonBatchResponse(2, 1, 1, List.of()));
        observe(batchMethod, new Object[]{List.of()}, batch).await().indefinitely();

        assertEquals(1, records.size());
        assertEquals("WARN", records.get(0).getLevel().getName());
        assertEquals("Business operation completed operation=lesson.batch.create total=2 succeeded=1 failed=1",
                message(0));
    }

    @Test
    void cancellationClearsBatchScopeAndIndependentSubscriptionsStayVisible() {
        Context shared = Context.empty();
        var pending = observe(createMethod(), new Object[]{null}, Uni.createFrom().nothing())
                .subscribe().with(shared, ignored -> fail("Cancelled operation must not complete"));
        pending.cancel();
        assertTrue(records.isEmpty());

        for (int i = 0; i < 2; i++) {
            observe(createMethod(), new Object[]{null},
                    Uni.createFrom().item(new LessonResponse(8L, 1, "secret", "secret")))
                    .subscribe().with(shared, ignored -> {
                    }, Assertions::fail);
        }

        assertEquals(2, records.size());
    }

    @Test
    void adminSuccessKeepsResultIdentityAndLogsNoPayload() throws Exception {
        Method method = VocabularyCoreEditService.class.getMethod(
                "updateVocabularyCore", Long.class, VocabularyCoreUpdateRequest.class);

        var result = new VocabularyCoreResponse(42L);

        assertSame(result, observe(method, new Object[]{42L, null},
                Uni.createFrom().item(result)).await().indefinitely());
        assertEquals("Business operation completed operation=vocabulary.core.update vocabularyId=42", message(0));
    }

    @Test
    void loggingInterceptorRunsOutsideTheExistingTransactionInterceptor() throws Exception {
        int transactionPriority = Class.forName(
                        "io.quarkus.hibernate.reactive.panache.common.runtime.WithTransactionInterceptor"
                )
                .getAnnotation(Priority.class).value();

        assertTrue(VocabularyOperationLog.class.getAnnotation(Priority.class).value() < transactionPriority);
    }

    private String message(int index) {
        return records.get(index).getFormattedMessage();
    }

    private Method createMethod() {
        try {
            return LessonService.class.getMethod("createLesson", LessonWriteRequest.class);
        } catch (NoSuchMethodException failure) {
            throw new AssertionError(failure);
        }
    }

    private Uni<?> observe(Method method, Object[] parameters, Uni<?> result) {
        InvocationContext invocation = (InvocationContext) Proxy.newProxyInstance(
                getClass().getClassLoader(), new Class<?>[]{InvocationContext.class},
                (proxy, invoked, arguments) -> switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getParameters" -> parameters;
                    case "proceed" -> result;
                    default -> throw new UnsupportedOperationException(invoked.getName());
                });
        try {
            return (Uni<?>) new VocabularyOperationLog().log(invocation);
        } catch (Exception failure) {
            throw new AssertionError(failure);
        }
    }
}
