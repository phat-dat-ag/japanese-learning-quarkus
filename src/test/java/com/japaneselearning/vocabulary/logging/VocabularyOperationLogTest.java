package com.japaneselearning.vocabulary.logging;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.vocabulary.admin.service.VocabularyAssignmentEditService;
import com.japaneselearning.vocabulary.admin.service.VocabularyReadingEditService;
import com.japaneselearning.vocabulary.admin.service.VocabularyMeaningEditService;
import com.japaneselearning.vocabulary.admin.service.VocabularyExampleEditService;
import com.japaneselearning.vocabulary.admin.service.VocabularyKanjiEditService;
import com.japaneselearning.vocabulary.admin.service.VocabularyPitchAccentEditService;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.service.VocabularyService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
            ((ExtLogRecord) record).copyMdc();
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
        assertEquals("lesson.create.completed", records.get(0).getMdc("event.name"));
        assertEquals("Successfully created lesson with id=7", message(0));
        assertEquals("7", records.get(0).getMdc("lessonId"));
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
        assertEquals("vocabulary.core.update.rejected", records.get(0).getMdc("event.name"));
        assertTrue(message(0).startsWith("Vocabulary core update rejected because"));
        assertFalse(message(0).contains("sensitive-payload"));
        assertEquals("42", records.get(0).getMdc("vocabularyId"));
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

        assertEquals(2, records.size());
        assertEquals("lesson.batch.create.started", records.get(0).getMdc("event.name"));
        assertEquals("WARN", records.get(1).getLevel().getName());
        assertEquals("lesson.batch.create.completed", records.get(1).getMdc("event.name"));
        assertEquals("Lesson batch completed: processed=2, created=1, failed=1", message(1));
        assertEquals("2", records.get(1).getMdc("total"));
        assertEquals("1", records.get(1).getMdc("succeeded"));
        assertEquals("1", records.get(1).getMdc("failed"));
        assertEquals("partial_failure", records.get(1).getMdc("outcome"));
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
        assertEquals("vocabulary.core.update.completed", records.get(0).getMdc("event.name"));
        assertEquals("Successfully updated vocabulary entry with id=42", message(0));
    }

    @Test
    void loggingInterceptorRunsOutsideTheExistingTransactionInterceptor() throws Exception {
        int transactionPriority = Class.forName(
                        "io.quarkus.hibernate.reactive.panache.common.runtime.WithTransactionInterceptor"
                )
                .getAnnotation(Priority.class).value();

        assertTrue(VocabularyOperationLog.class.getAnnotation(Priority.class).value() < transactionPriority);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 3})
    void lessonRetrievalUsesActualCountsAndNeverWarnsForEmptyResults(int count) throws Exception {
        Method method = LessonService.class.getMethod("getLessonsByLevel", String.class);
        var lessons = java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> new LessonResponse((long) i, i, "secret", "secret")).toList();

        observe(method, new Object[]{"secret-query"}, Uni.createFrom().item(lessons)).await().indefinitely();

        assertEquals(1, records.size());
        assertEquals("INFO", records.get(0).getLevel().getName());
        assertEquals("lesson.list.completed", records.get(0).getMdc("event.name"));
        assertEquals(Integer.toString(count), records.get(0).getMdc("count"));
        assertEquals("Successfully retrieved " + count + " lessons for the requested JLPT level", message(0));
        assertFalse(message(0).contains("secret"));
    }

    @Test
    void missingLevelAndBusinessValidationAreSingleWarnings() {
        var failures = List.of(
                new ResourceNotFoundException("JLPT_LEVEL_NOT_FOUND", "secret-level"),
                new ValidationException("VALIDATION_ERROR", "secret-input",
                        List.of(new ValidationError("secret-field", "secret-value")))
        );
        for (var failure : failures) {
            records.clear();
            var request = new LessonWriteRequest(99L, 1, "secret", "secret", 1);
            assertSame(failure, assertThrows(failure.getClass(), () ->
                    observe(createMethod(), new Object[]{request}, Uni.createFrom().failure(failure))
                            .await().indefinitely()));
            assertEquals(1, records.size());
            assertEquals("WARN", records.get(0).getLevel().getName());
            assertEquals("99", records.get(0).getMdc("levelId"));
            assertEquals("failure", records.get(0).getMdc("outcome"));
            assertFalse(message(0).contains("secret"));
            assertNull(records.get(0).getThrown());
        }
    }

    @Test
    void everyAdminMutationHasDescriptiveCountOrIdentifierAfterSuccess() {
        for (Class<?> service : List.of(
                VocabularyCoreEditService.class, VocabularyAssignmentEditService.class,
                VocabularyReadingEditService.class, VocabularyMeaningEditService.class,
                VocabularyExampleEditService.class, VocabularyKanjiEditService.class,
                VocabularyPitchAccentEditService.class
        )) {
            for (Method method : service.getDeclaredMethods()) {
                LogVocabularyOperation annotation = method.getAnnotation(LogVocabularyOperation.class);
                if (annotation == null) {
                    continue;
                }
                records.clear();
                Object[] parameters = java.util.Arrays.stream(method.getParameterTypes())
                        .map(type -> type == Long.class ? 42L : null).toArray();
                Object result = annotation.value().endsWith(".add") ? List.of(1L, 2L) : new Object();
                observe(method, parameters, Uni.createFrom().item(result)).await().indefinitely();
                assertEquals(1, records.size(), method.getName());
                assertEquals(annotation.value() + ".completed", records.get(0).getMdc("event.name"));
                assertTrue(message(0).startsWith("Successfully "));
                assertTrue(message(0).contains("vocabulary entry with id=42"));
                assertFalse(message(0).contains("{"));
                assertFalse(message(0).contains("unavailable"));
                if (result instanceof List<?>) {
                    assertEquals("2", records.get(0).getMdc("count"));
                    assertTrue(message(0).contains("added 2 "));
                }
            }
        }
    }

    @Test
    void importSummaryWaitsForCommitAndIncludesActualCounts() throws Exception {
        Method method = VocabularyService.class.getMethod("importVocabulary", List.class);
        AtomicReference<UniEmitter<? super ImportResult>> commit = new AtomicReference<>();
        Uni<ImportResult> pending = Uni.createFrom().<ImportResult>emitter(commit::set);
        Uni<?> observed = observe(method, new Object[]{List.of()}, pending);
        assertTrue(records.isEmpty());
        observed.subscribe().with(ignored -> {
        }, Assertions::fail);
        assertEquals(1, records.size());
        assertEquals("vocabulary.import.started", records.get(0).getMdc("event.name"));
        commit.get().complete(new ImportResult(5, 2, 3));
        assertEquals(2, records.size());
        assertEquals("vocabulary.import.completed", records.get(1).getMdc("event.name"));
        assertEquals("5", records.get(1).getMdc("total"));
        assertEquals("2", records.get(1).getMdc("created"));
        assertEquals("3", records.get(1).getMdc("updated"));
        assertEquals("Vocabulary import completed: processed=5, created=2, updated=3; updated counts existing entries processed",
                message(1));
    }

    @Test
    void failedAtomicImportNeverReportsCommittedOrPartialCounts() throws Exception {
        Method method = VocabularyService.class.getMethod("importVocabulary", List.class);
        var failure = new IllegalStateException("secret SQL and password");
        var result = observe(method, new Object[]{List.of()}, Uni.createFrom().failure(failure));
        assertSame(failure, assertThrows(IllegalStateException.class, () -> result.await().indefinitely()));
        assertEquals(1, records.size());
        assertEquals("vocabulary.import.started", records.get(0).getMdc("event.name"));
        assertNull(records.get(0).getMdc("created"));
    }

    @Test
    void debugFailureHasSafeContextButNoDuplicateStackAndRestoresMdc() {
        var previousLevel = logger.getLevel();
        org.jboss.logmanager.MDC.put("correlationId", "test-correlation");
        logger.setLevel(java.util.logging.Level.FINE);
        try {
            var failure = new IllegalStateException("secret SQL and password");
            assertThrows(IllegalStateException.class, () ->
                    observe(createMethod(), new Object[]{null}, Uni.createFrom().failure(failure))
                            .await().indefinitely());
            assertEquals(2, records.size());
            assertEquals("lesson.create.failed", records.get(1).getMdc("event.name"));
            assertTrue(message(1).contains("centralized exception diagnostic"));
            assertEquals("test-correlation", records.get(1).getMdc("correlationId"));
            assertNull(records.get(1).getThrown());
            assertFalse(message(1).contains("secret"));
            assertNull(org.jboss.logmanager.MDC.get("event.name"));
        } finally {
            logger.setLevel(previousLevel);
            org.jboss.logmanager.MDC.remove("correlationId");
        }
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
