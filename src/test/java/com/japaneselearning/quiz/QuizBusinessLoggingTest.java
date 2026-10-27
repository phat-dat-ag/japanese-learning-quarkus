package com.japaneselearning.quiz;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.player.service.QuizAnswerService;
import com.japaneselearning.quiz.player.dto.QuizAnswerRequest;
import com.japaneselearning.quiz.entity.QuizAnswer;
import com.japaneselearning.quiz.entity.QuizSessionOption;
import com.japaneselearning.quiz.entity.QuizSessionQuestion;
import com.japaneselearning.quiz.repository.QuizSessionQuestionRepository;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.quiz.admin.dto.QuizImportError;
import com.japaneselearning.quiz.admin.dto.QuizQuestionCreateRequest;
import com.japaneselearning.quiz.admin.dto.QuizQuestionFilter;
import com.japaneselearning.quiz.admin.dto.QuizQuestionResponse;
import com.japaneselearning.quiz.admin.repository.QuizAdminRepository;
import com.japaneselearning.quiz.admin.service.QuizAdminQueryService;
import com.japaneselearning.quiz.admin.service.QuizAdminWriteService;
import com.japaneselearning.quiz.admin.service.QuizImportItem;
import com.japaneselearning.quiz.admin.service.QuizImportService;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;
import com.japaneselearning.quiz.domain.QuizSessionStatus;
import com.japaneselearning.quiz.entity.QuizQuestion;
import com.japaneselearning.quiz.entity.QuizQuestionLesson;
import com.japaneselearning.quiz.entity.QuizQuestionLevel;
import com.japaneselearning.quiz.entity.QuizQuestionOption;
import com.japaneselearning.quiz.entity.QuizSession;
import com.japaneselearning.quiz.player.service.QuizGameService;
import com.japaneselearning.quiz.player.service.QuizHistoryService;
import com.japaneselearning.quiz.repository.QuizAnswerRepository;
import com.japaneselearning.quiz.repository.QuizSessionRepository;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import jakarta.interceptor.InvocationContext;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

class QuizBusinessLoggingTest {
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

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        logger.setLevel(java.util.logging.Level.INFO);
        logger.addHandler(handler);
        MDC.put("correlationId", "quiz-request-42");
        MDC.put("traceId", "0123456789abcdef0123456789abcdef");
        MDC.put("spanId", "0123456789abcdef");
    }

    @AfterEach
    void release() {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
        MDC.clear();
    }

    @Test
    void questionSearchUsesReturnedPageCountAndDoesNotExposeKeywordOrContent() throws Exception {
        var repository = new QuizAdminRepository() {
            public Uni<List<QuizQuestion>> search(QuizQuestionFilter filter) {
                return Uni.createFrom().item(List.of(question(15L), question(16L)));
            }

            public Uni<Long> countMatching(QuizQuestionFilter filter) {
                return Uni.createFrom().item(9L);
            }

            public Uni<List<QuizQuestionOption>> options(List<Long> ids) {
                return Uni.createFrom().item(List.of());
            }

            public Uni<List<QuizQuestionLevel>> levels(List<Long> ids) {
                return Uni.createFrom().item(List.of());
            }

            public Uni<List<QuizQuestionLesson>> lessons(List<Long> ids) {
                return Uni.createFrom().item(List.of());
            }

            public Uni<List<Object[]>> sources(List<Long> ids) {
                return Uni.createFrom().item(List.of());
            }
        };
        var service = new QuizAdminQueryService(repository);
        var filter = new QuizQuestionFilter(null, null, null, null, null, null, "secret-keyword", 0, 2);
        var result = observe(service, "list", new Class<?>[]{QuizQuestionFilter.class}, new Object[]{filter}, service.list(filter));
        assertTrue(records.isEmpty());
        result.await().indefinitely();
        var event = only("quiz.question.list.completed");
        assertEquals("Successfully retrieved 2 quiz questions from 9 matching questions", event.getFormattedMessage());
        assertEquals("2", event.getMdc("count"));
        assertEquals("9", event.getMdc("total"));
        assertEquals("quiz-request-42", event.getMdc("correlationId"));
        assertEquals("0123456789abcdef", event.getMdc("spanId"));
        assertFalse(event.getFormattedMessage().contains("secret"));
        assertNull(event.getMdc("keyword"));
    }

    @Test
    void emptyHistoryIsInfoAndMissingSessionIsWarnWithoutDisclosingOwnership() throws Exception {
        var repository = new QuizSessionRepository() {
            public Uni<Long> countCompletedBySubject(String subject) {
                return Uni.createFrom().item(0L);
            }

            public Uni<List<QuizSession>> findCompletedBySubject(String subject, int page, int size) {
                return Uni.createFrom().item(List.of());
            }

            public Uni<QuizSession> findOwnedById(Long id, String subject) {
                return Uni.createFrom().nullItem();
            }
        };
        var answerRepository = new QuizAnswerRepository() {
            public Uni<Map<Long, AnswerCounts>> countBySessions(List<Long> ids) {
                assertTrue(ids.isEmpty());
                return Uni.createFrom().item(Map.of());
            }
        };
        var service = new QuizHistoryService(repository, null, answerRepository);
        observe(service, "list", new Class<?>[]{String.class, int.class, int.class},
                new Object[]{"secret-subject", 0, 20}, service.list("secret-subject", 0, 20)).await().indefinitely();
        var empty = only("quiz.history.list.completed");
        assertEquals(org.jboss.logmanager.Level.INFO, empty.getLevel());
        assertEquals("0", empty.getMdc("count"));
        records.clear();
        var missing = observe(service, "detail", new Class<?>[]{String.class, Long.class},
                new Object[]{"secret-subject", 15L}, service.detail("secret-subject", 15L));
        assertThrows(ResourceNotFoundException.class, () -> missing.await().indefinitely());
        var rejected = only("quiz.history.detail.rejected");
        assertEquals(org.jboss.logmanager.Level.WARN, rejected.getLevel());
        assertEquals("QUIZ_SESSION_NOT_FOUND", rejected.getMdc("error.code"));
        assertTrue(rejected.getFormattedMessage().contains("not found or is unavailable"));
        assertFalse(rejected.getFormattedMessage().contains("secret"));
        assertNull(rejected.getMdc("subject"));
    }

    @Test
    void sessionCompletionIsLoggedOnlyAfterFlushAndRepeatIsRejected() throws Exception {
        var session = new QuizSession();
        session.id = 42L;
        session.status = QuizSessionStatus.IN_PROGRESS;
        session.questionCount = 2;
        var flush = new AtomicReference<UniEmitter<? super Void>>();
        var repository = new QuizSessionRepository() {
            public Uni<QuizSession> findOwnedByIdForUpdate(Long id, String subject) {
                return Uni.createFrom().item(session);
            }

            public Uni<Void> flush() {
                return Uni.createFrom().<Void>emitter(flush::set);
            }
        };
        var answers = new QuizAnswerRepository() {
            public Uni<AnswerCounts> countBySession(Long id) {
                return Uni.createFrom().item(new AnswerCounts(2, 1));
            }
        };
        var service = new QuizGameService(null, null, repository, null, answers);
        var result = observe(service, "finish", new Class<?>[]{String.class, Long.class},
                new Object[]{"secret-subject", 42L}, service.finish("secret-subject", 42L));
        result.subscribe().with(ignored -> {
        }, failure -> fail(failure));
        assertTrue(records.isEmpty());
        flush.get().complete(null);
        assertEquals(QuizSessionStatus.COMPLETED, session.status);
        assertEquals("Successfully completed quiz session with sessionId=42", only("quiz.session.finish.completed").getFormattedMessage());
        records.clear();
        var repeated = observe(service, "finish", new Class<?>[]{String.class, Long.class},
                new Object[]{"secret-subject", 42L}, service.finish("secret-subject", 42L));
        assertThrows(ConflictException.class, () -> repeated.await().indefinitely());
        assertEquals("QUIZ_FINISH_CONFLICT", only("quiz.session.finish.rejected").getMdc("error.code"));
    }

    @Test
    void exhaustedSessionNextIsAnInfoOutcomeWithZeroQuestions() throws Exception {
        var session = new QuizSession();
        session.id = 42L;
        session.status = QuizSessionStatus.COMPLETED;
        var repository = new QuizSessionRepository() {
            public Uni<QuizSession> findOwnedById(Long id, String subject) {
                return Uni.createFrom().item(session);
            }
        };
        var service = new QuizGameService(null, null, repository, null, null);
        observe(service, "next", new Class<?>[]{String.class, Long.class}, new Object[]{"secret", 42L},
                service.next("secret", 42L)).await().indefinitely();
        var event = only("quiz.session.next.completed");
        assertEquals("0", event.getMdc("count"));
        assertTrue(event.getFormattedMessage().startsWith("No next question is available for sessionId=42"));
        assertEquals(org.jboss.logmanager.Level.INFO, event.getLevel());
    }

    @Test
    void partialImportReportsActualCreatedAndFailedCountsWithoutItemLogs() throws Exception {
        var writes = new QuizAdminWriteService(null, null, null, null, null) {
            public Uni<QuizQuestionResponse> create(QuizQuestionCreateRequest request) {
                return Uni.createFrom().item(new QuizQuestionResponse(15L, 0L,
                        QuestionSource.CUSTOM, QuestionStatus.DRAFT, null, null, "secret-content", 0, 1,
                        "secret-target", null, null, false, false, List.of(), List.of(), List.of(), null, null));
            }
        };
        var service = new QuizImportService(writes);
        var items = List.of(new QuizImportItem(new QuizQuestionCreateRequest(null, null), List.of()),
                new QuizImportItem(null, List.of(new QuizImportError("secret-field", "INVALID", "secret-value"))));
        observe(service, "importQuestions", new Class<?>[]{List.class}, new Object[]{items},
                service.importQuestions(items)).await().indefinitely();
        var event = only("quiz.question.import.partial_failure");
        assertEquals("Quiz import completed: processed=2, created=1, failed=1", event.getFormattedMessage());
        assertEquals("1", event.getMdc("created"));
        assertEquals("1", event.getMdc("failed"));
        assertEquals(org.jboss.logmanager.Level.WARN, event.getLevel());
        assertFalse(event.getFormattedMessage().contains("secret"));
    }

    @Test
    void acceptedAnswerReportsActualProgressAndForeignOptionIsRejectedWithoutPersistence() throws Exception {
        var session = new QuizSession();
        session.id = 42L;
        session.status = QuizSessionStatus.IN_PROGRESS;
        session.questionCount = 2;
        var sessions = new QuizSessionRepository() {
            public Uni<QuizSession> findOwnedByIdForUpdate(Long id, String subject) {
                return Uni.createFrom().item(session);
            }
        };
        var snapshot = new QuizSessionQuestion();
        snapshot.id = 9L;
        snapshot.sentenceReading = "secret-question";
        var option = new QuizSessionOption();
        option.id = 3L;
        option.correct = true;
        option.optionText = "secret-answer";
        var questions = new QuizSessionQuestionRepository() {
            public Uni<QuizSessionQuestion> findNextUnanswered(Long id) {
                return Uni.createFrom().item(snapshot);
            }

            public Uni<List<QuizSessionOption>> findOptions(Long id) {
                return Uni.createFrom().item(List.of(option));
            }
        };
        var writes = new java.util.concurrent.atomic.AtomicInteger();
        var answers = new QuizAnswerRepository() {
            public Uni<QuizAnswer> persistAndFlush(QuizAnswer answer) {
                writes.incrementAndGet();
                return Uni.createFrom().item(answer);
            }

            public Uni<AnswerCounts> countBySession(Long id) {
                return Uni.createFrom().item(new AnswerCounts(1, 1));
            }
        };
        var service = new QuizAnswerService(sessions, questions, answers);
        var request = new QuizAnswerRequest(9L, 3L);
        observe(service, "submit", new Class<?>[]{String.class, Long.class, QuizAnswerRequest.class},
                new Object[]{"secret-subject", 42L, request}, service.submit("secret-subject", 42L, request))
                .await().indefinitely();
        var event = only("quiz.answer.completed");
        assertEquals("Successfully recorded an answer for sessionId=42; answered=1 of 2 questions", event.getFormattedMessage());
        assertEquals("1", event.getMdc("count"));
        assertEquals("2", event.getMdc("total"));
        assertEquals("9", event.getMdc("itemId"));
        assertFalse(event.getFormattedMessage().contains("secret"));
        records.clear();
        var invalid = new QuizAnswerRequest(9L, 99L);
        var rejected = observe(service, "submit", new Class<?>[]{String.class, Long.class, QuizAnswerRequest.class},
                new Object[]{"secret-subject", 42L, invalid}, service.submit("secret-subject", 42L, invalid));
        assertThrows(ValidationException.class, () -> rejected.await().indefinitely());
        assertEquals("QUIZ_ANSWER_INVALID", only("quiz.answer.rejected").getMdc("error.code"));
        assertEquals(1, writes.get());
    }

    private QuizQuestion question(Long id) {
        var question = new QuizQuestion();
        question.id = id;
        question.sourceType = QuestionSource.CUSTOM;
        question.status = QuestionStatus.DRAFT;
        question.sentenceReading = "secret-question-content";
        return question;
    }

    private ExtLogRecord only(String event) {
        assertEquals(1, records.size());
        assertEquals(event, records.get(0).getMdc("event.name"));
        assertNotNull(records.get(0).getMdc("durationMs"));
        assertNull(records.get(0).getThrown());
        return records.get(0);
    }

    // Simulate the transaction interceptor's result at the existing outer logging boundary.
    private Uni<?> observe(Object target, String name, Class<?>[] types, Object[] parameters, Uni<?> result) throws Exception {
        Method method = target.getClass().getMethod(name, types);
        var invocation = (InvocationContext) Proxy.newProxyInstance(getClass().getClassLoader(),
                new Class<?>[]{InvocationContext.class}, (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getTarget" -> target;
                    case "getParameters" -> parameters;
                    case "proceed" -> result;
                    default -> throw new UnsupportedOperationException(invoked.getName());
                });
        var interceptor = new OperationLog();
        var threshold = OperationLog.class.getDeclaredField("slowOperationMs");
        threshold.setAccessible(true);
        threshold.setLong(interceptor, Long.MAX_VALUE);
        var intercept = OperationLog.class.getDeclaredMethod("log", InvocationContext.class);
        intercept.setAccessible(true);
        try {
            return (Uni<?>) intercept.invoke(interceptor, invocation);
        } catch (InvocationTargetException failure) {
            throw new AssertionError(failure.getCause());
        }
    }
}
