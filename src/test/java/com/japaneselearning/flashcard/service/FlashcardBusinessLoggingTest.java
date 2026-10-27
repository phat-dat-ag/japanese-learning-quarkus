package com.japaneselearning.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.flashcard.domain.FlashcardSessionMode;
import com.japaneselearning.flashcard.domain.FlashcardSessionStatus;
import com.japaneselearning.flashcard.domain.LearningStatus;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.domain.ReviewRating;
import com.japaneselearning.flashcard.dto.FlashcardReviewRequest;
import com.japaneselearning.flashcard.dto.FlashcardSessionCreateRequest;
import com.japaneselearning.flashcard.entity.FlashcardReview;
import com.japaneselearning.flashcard.entity.FlashcardSession;
import com.japaneselearning.flashcard.entity.FlashcardSessionItem;
import com.japaneselearning.flashcard.entity.VocabularyBookmark;
import com.japaneselearning.flashcard.entity.VocabularyProgress;
import com.japaneselearning.flashcard.repository.FlashcardLearningRepository;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.flashcard.repository.FlashcardReviewRepository;
import com.japaneselearning.flashcard.repository.FlashcardSessionRepository;
import com.japaneselearning.flashcard.repository.VocabularyBookmarkRepository;
import com.japaneselearning.flashcard.repository.VocabularyProgressRepository;
import com.japaneselearning.security.JwksTestResource;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.entity.PartOfSpeech;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import io.quarkus.arc.ClientProxy;
import io.quarkus.hibernate.reactive.panache.PanacheQuery;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import jakarta.interceptor.InvocationContext;
import jakarta.persistence.OptimisticLockException;
import org.hibernate.reactive.common.spi.Implementor;
import org.hibernate.reactive.context.impl.BaseKey;
import org.hibernate.reactive.mutiny.Mutiny;
import org.jboss.logmanager.ExtLogRecord;
import org.jboss.logmanager.MDC;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertSame;

@QuarkusTest
@QuarkusTestResource(JwksTestResource.class)
class FlashcardBusinessLoggingTest {
    private static final String OWNER = "private-subject-sentinel";
    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Inject
    Mutiny.SessionFactory factory;
    @Inject
    ObjectMapper json;

    private final List<ExtLogRecord> records = new CopyOnWriteArrayList<>();
    private final Logger operationLogger = Logger.getLogger(OperationLog.class.getName());
    private final Logger featureLogger = Logger.getLogger("com.japaneselearning.flashcard");
    private java.util.logging.Level oldOperationLevel;
    private java.util.logging.Level oldFeatureLevel;
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

    private final SessionRows sessionRows = new SessionRows();
    private final Catalogue catalogue = new Catalogue();
    private final ProgressRows progressRows = new ProgressRows();
    private final ReviewRows reviewRows = new ReviewRows();
    private final BookmarkRows bookmarkRows = new BookmarkRows();
    private final FlashcardTime time = new FlashcardTime(Clock.fixed(NOW, ZoneOffset.UTC));
    private final VocabularyProgressService progress = new VocabularyProgressService(progressRows);
    private final FlashcardSessionService sessions = new FlashcardSessionService(sessionRows, catalogue, progress, time);
    private final LearningStatisticsService statistics = new LearningStatisticsService(catalogue, progress, bookmarkRows, time);
    private Supplier<Uni<Void>> commit = () -> Uni.createFrom().voidItem();
    private int commits;
    private int rollbacks;

    @BeforeEach
    void capture() {
        oldOperationLevel = operationLogger.getLevel();
        oldFeatureLevel = featureLogger.getLevel();
        operationLogger.setLevel(java.util.logging.Level.FINE);
        featureLogger.setLevel(java.util.logging.Level.FINE);
        operationLogger.addHandler(handler);
        featureLogger.addHandler(handler);
    }

    @AfterEach
    void release() {
        operationLogger.removeHandler(handler);
        featureLogger.removeHandler(handler);
        operationLogger.setLevel(oldOperationLevel);
        featureLogger.setLevel(oldFeatureLevel);
        for (var event : records) {
            assertFalse(event.getFormattedMessage().contains(OWNER));
            assertFalse(event.getFormattedMessage().contains("payload-sentinel"));
            assertFalse(event.getMdcCopy().toString().contains(OWNER));
            assertFalse(event.getMdcCopy().toString().contains("payload-sentinel"));
            assertNull(event.getThrown(), "Only central exception diagnostics may contain a stack");
        }
    }

    @Test
    void creationSelectionAndEmptyOutcomeUseActualCounts() throws Throwable {
        call(sessions, "create", new Class<?>[]{String.class, FlashcardSessionCreateRequest.class},
                OWNER, new FlashcardSessionCreateRequest(7L, FlashcardSessionMode.NEW_AND_DUE, 20, false));
        var created = only("flashcard.session.creation.completed");
        assertEquals("Successfully created learning session with id=10 containing 1 cards", created.getFormattedMessage());
        assertEquals("1", created.getMdc("count"));
        assertEquals("20", only("flashcard.session.selection").getMdc("size"));
        assertNull(only("flashcard.session.selection").getMdc("total"));
        assertEquals(1, commits);
        records.clear();
        catalogue.ids = List.of();
        call(sessions, "create", new Class<?>[]{String.class, FlashcardSessionCreateRequest.class},
                OWNER, new FlashcardSessionCreateRequest(7L, FlashcardSessionMode.DUE, 20, false));
        var empty = only("flashcard.session.creation.completed");
        assertEquals("no_eligible_cards", empty.getMdc("reason"));
        assertEquals("0", empty.getMdc("count"));
        assertTrue(empty.getFormattedMessage().contains("No learning session was created"));
        assertEquals(org.jboss.logmanager.Level.INFO, empty.getLevel());
        assertEquals(1, sessionRows.inserts);
    }

    @Test
    void resumeListFinishAndAbandonDescribeActualState() throws Throwable {
        call(sessions, "get", new Class<?>[]{String.class, Long.class}, OWNER, 10L);
        assertTrue(only("flashcard.session.get.completed").getFormattedMessage().contains("for resumption"));
        records.clear();
        call(sessions, "list", new Class<?>[]{String.class, Long.class, FlashcardSessionStatus.class, int.class, int.class},
                OWNER, null, null, 0, 20);
        assertEquals("1", only("flashcard.session.list.completed").getMdc("count"));
        assertEquals("3", only("flashcard.session.list.completed").getMdc("total"));
        records.clear();
        assertThrows(ConflictException.class, () -> call(sessions, "finish", new Class<?>[]{String.class, Long.class}, OWNER, 10L));
        assertEquals(org.jboss.logmanager.Level.WARN, only("flashcard.session.finish.rejected").getLevel());
        assertNo("flashcard.session.finish.completed");
        records.clear();
        call(sessions, "abandon", new Class<?>[]{String.class, Long.class}, OWNER, 10L);
        assertTrue(only("flashcard.session.abandon.completed").getFormattedMessage().contains("Successfully abandoned"));
        records.clear();
        call(sessions, "abandon", new Class<?>[]{String.class, Long.class}, OWNER, 10L);
        assertTrue(only("flashcard.session.abandon.unchanged").getFormattedMessage().contains("remains ABANDONED"));
        assertNo("flashcard.session.abandon.completed");
        sessionRows.row.status = FlashcardSessionStatus.COMPLETED;
        records.clear();
        call(sessions, "finish", new Class<?>[]{String.class, Long.class}, OWNER, 10L);
        assertTrue(only("flashcard.session.finish.completed").getFormattedMessage().contains("already completed"));
        records.clear();
        call(sessions, "abandon", new Class<?>[]{String.class, Long.class}, OWNER, 10L);
        assertTrue(only("flashcard.session.abandon.unchanged").getFormattedMessage().contains("remains COMPLETED"));
    }

    @Test
    void acceptedReviewCompletesSessionAndReplayDoesNotWriteAgain() throws Throwable {
        var reviews = reviews();
        review(reviews, ReviewRating.GOOD, 0L);
        var accepted = only("flashcard.review.accepted");
        assertTrue(accepted.getFormattedMessage().contains("updated learning progress was saved"));
        assertTrue(accepted.getFormattedMessage().contains("successfully completed the learning session"));
        assertEquals("GOOD", accepted.getMdc("reason"));
        assertEquals("86400", accepted.getMdc("intervalSeconds"));
        assertEquals("42", accepted.getMdc("vocabularyId"));
        assertEquals(1, progressRows.writes);
        assertEquals(1, reviewRows.writes);
        records.clear();
        review(reviews, ReviewRating.GOOD, 0L);
        assertTrue(only("flashcard.review.replayed").getFormattedMessage().contains("no review or progress was written"));
        assertNo("flashcard.review.accepted");
        assertNo("flashcard.srs.scheduled");
        assertEquals(1, progressRows.writes);
        assertEquals(1, reviewRows.writes);
        records.clear();
        assertThrows(ConflictException.class, () -> review(reviews, ReviewRating.EASY, 0L));
        assertEquals("replay_mismatch", only("flashcard.review.validation_failed").getMdc("reason"));
        only("flashcard.review.rejected");
    }

    @Test
    void practiceRecordsHistoryWithoutScheduling() throws Throwable {
        sessionRows.row.mode = FlashcardSessionMode.ALL;
        review(reviews(), ReviewRating.AGAIN, 9L);
        assertEquals(0, progressRows.writes);
        assertEquals(1, reviewRows.writes);
        assertNo("flashcard.srs.scheduled");
        assertTrue(only("flashcard.review.accepted").getFormattedMessage().contains("practice left the SRS schedule unchanged"));
    }

    @Test
    void notDueAndStaleProgressAreRejectedWithoutWrites() throws Throwable {
        progressRows.row = progressRow(NOW.plusSeconds(60));
        var reviews = reviews();
        assertThrows(ConflictException.class, () -> review(reviews, ReviewRating.GOOD, 1L));
        assertEquals("FLASHCARD_NOT_DUE", only("flashcard.review.rejected").getMdc("error.code"));
        assertEquals("not_due", only("flashcard.review.validation_failed").getMdc("reason"));
        records.clear();
        progressRows.row.nextReviewAt = FlashcardTime.stored(NOW.minusSeconds(1));
        assertThrows(ConflictException.class, () -> review(reviews, ReviewRating.GOOD, 0L));
        assertEquals("progress_version_mismatch", only("flashcard.review.validation_failed").getMdc("reason"));
        assertNo("flashcard.review.accepted");
        assertEquals(0, reviewRows.writes);
        assertEquals(0, progressRows.writes);
    }

    @Test
    void ownershipItemAndSessionStateRejectionsRemainDistinct() throws Throwable {
        var reviews = reviews();
        assertThrows(ResourceNotFoundException.class, () -> call(reviews, "submit",
                new Class<?>[]{String.class, Long.class, Long.class, FlashcardReviewRequest.class},
                OWNER, 10L, 999L, new FlashcardReviewRequest(ReviewRating.GOOD, 0L)));
        assertEquals("item_not_in_session", only("flashcard.review.validation_failed").getMdc("reason"));
        records.clear();
        sessionRows.row.status = FlashcardSessionStatus.ABANDONED;
        assertThrows(ConflictException.class, () -> review(reviews, ReviewRating.GOOD, 0L));
        assertTrue(only("flashcard.review.validation_failed").getFormattedMessage().contains("ABANDONED"));
        records.clear();
        sessionRows.row = null;
        assertThrows(ResourceNotFoundException.class, () -> review(reviews, ReviewRating.GOOD, 0L));
        assertEquals("FLASHCARD_SESSION_NOT_FOUND", only("flashcard.review.rejected").getMdc("error.code"));
        assertTrue(only("flashcard.review.rejected").getFormattedMessage().contains("not found or is unavailable"));
        assertNo("flashcard.review.accepted");
    }

    @Test
    void commitFailureSuppressesAcceptedAndCompletionClaims() {
        var failure = new IllegalStateException("payload-sentinel");
        commit = () -> {
            assertNo("flashcard.review.accepted");
            return Uni.createFrom().failure(failure);
        };
        assertSame(failure, assertThrows(IllegalStateException.class, () -> review(reviews(), ReviewRating.GOOD, 0L)));
        assertNo("flashcard.review.accepted");
        only("flashcard.review.failed");
        assertEquals(1, rollbacks);
    }

    @Test
    void concurrentCommitFailureIsTranslatedAndNeverLoggedAsAccepted() {
        commit = () -> Uni.createFrom().failure(new OptimisticLockException("payload-sentinel"));
        assertThrows(ConflictException.class, () -> review(reviews(), ReviewRating.GOOD, 0L));
        assertNo("flashcard.review.accepted");
        assertEquals("FLASHCARD_CONFLICT", only("flashcard.review.rejected").getMdc("error.code"));
        assertEquals(OptimisticLockException.class.getName(), only("flashcard.concurrency.detected").getMdc("error.type"));
        assertEquals(1, rollbacks);
    }

    @Test
    void bookmarkOutcomesAndRollbackAreAccurate() throws Throwable {
        var service = new VocabularyBookmarkService(bookmarkRows, new CardRows(), time);
        call(service, "add", new Class<?>[]{String.class, Long.class}, OWNER, 42L);
        assertTrue(only("flashcard.bookmark.add.completed").getFormattedMessage().contains("ensured vocabulary is bookmarked"));
        records.clear();
        bookmarkRows.affected = 0;
        call(service, "add", new Class<?>[]{String.class, Long.class}, OWNER, 42L);
        only("flashcard.bookmark.add.unchanged");
        records.clear();
        call(service, "remove", new Class<?>[]{String.class, Long.class}, OWNER, 42L);
        assertEquals("1", only("flashcard.bookmark.remove.completed").getMdc("count"));
        records.clear();
        bookmarkRows.removed = 0;
        call(service, "remove", new Class<?>[]{String.class, Long.class}, OWNER, 42L);
        only("flashcard.bookmark.remove.unchanged");
        records.clear();
        commit = () -> Uni.createFrom().failure(new IllegalStateException("payload-sentinel"));
        assertThrows(IllegalStateException.class, () -> call(service, "add", new Class<?>[]{String.class, Long.class}, OWNER, 42L));
        assertNo("flashcard.bookmark.add.unchanged");
        assertNo("flashcard.bookmark.add.completed");
        only("flashcard.bookmark.add.failed");
    }

    @Test
    void collectionsStatisticsAndPersonalPagesHaveAccurateSummaries() throws Throwable {
        for (var collection : FlashcardLearningRepository.Collection.values()) {
            records.clear();
            call(statistics, "list", new Class<?>[]{String.class, String.class, Long.class,
                    FlashcardLearningRepository.Collection.class, int.class, int.class}, OWNER, "N5", 7L, collection, 0, 20);
            var result = only("flashcard.collection.completed");
            assertEquals("1", result.getMdc("count"));
            assertEquals("3", result.getMdc("total"));
            assertEquals(collection.name(), result.getMdc("reason"));
        }
        records.clear();
        call(statistics, "statistics", new Class<?>[]{String.class, String.class, Long.class}, OWNER, null, null);
        var result = only("flashcard.statistics.completed");
        assertTrue(result.getFormattedMessage().contains("studied percentage=33.33"));
        assertTrue(result.getFormattedMessage().contains("forgotten attempts=1"));
        assertTrue(result.getFormattedMessage().contains("practice attempts=1"));
        assertEquals("3", result.getMdc("total"));
        records.clear();
        var cards = new FlashcardService(new CardRows());
        call(cards, "getFlashcards", new Class<?>[]{String.class, Integer.class, int.class, int.class}, "N5", null, 0, 20);
        assertEquals("1", only("flashcard.catalogue.list.completed").getMdc("count"));
        assertEquals("3", only("flashcard.catalogue.list.completed").getMdc("total"));
        records.clear();
        call(new PersonalFlashcardService(cards, statistics), "list",
                new Class<?>[]{String.class, String.class, Integer.class, int.class, int.class}, OWNER, "N5", null, 0, 20);
        assertEquals("1", only("flashcard.personal.list.completed").getMdc("count"));
        assertNo("flashcard.catalogue.list.completed");
        records.clear();
        catalogue.cards = List.of();
        catalogue.total = 0;
        call(statistics, "list", new Class<?>[]{String.class, String.class, Long.class,
                        FlashcardLearningRepository.Collection.class, int.class, int.class},
                OWNER, null, null, FlashcardLearningRepository.Collection.DUE, 0, 20);
        assertEquals("0", only("flashcard.collection.completed").getMdc("count"));
        assertEquals(org.jboss.logmanager.Level.INFO, only("flashcard.collection.completed").getLevel());
    }

    @Test
    void srsDiagnosticsCoverEveryRatingAndMasteryWithoutChangingFormulas() {
        var srs = new SpacedRepetitionService(600, 365, 5, 21);
        for (var rating : ReviewRating.values()) {
            records.clear();
            var result = srs.review(ProgressState.unseen(), rating, NOW);
            var event = only("flashcard.srs.scheduled");
            assertEquals(rating.name(), event.getMdc("reason"));
            assertEquals(Long.toString(result.intervalSeconds()), event.getMdc("intervalSeconds"));
            assertTrue(event.getFormattedMessage().contains("NEW -> LEARNING"));
            assertTrue(event.getFormattedMessage().contains("not yet been committed"));
            assertEquals(org.jboss.logmanager.Level.DEBUG, event.getLevel());
        }
        records.clear();
        var before = new ProgressState(LearningStatus.REVIEW, 4, 0, 21 * 86400L, 2.5, NOW, NOW, null, 4);
        assertEquals(LearningStatus.MASTERED, srs.review(before, ReviewRating.GOOD, NOW).status());
        assertTrue(only("flashcard.srs.scheduled").getFormattedMessage().contains("REVIEW -> MASTERED"));
    }

    @Test
    void delayedCommitProducesExactlyOneAcceptedEvent() throws Throwable {
        commit = () -> Uni.createFrom().emitter(emitter -> {
            assertNo("flashcard.review.accepted");
            Vertx.currentContext().runOnContext(ignored -> {
                assertNo("flashcard.review.accepted");
                emitter.complete(null);
            });
        });
        review(reviews(), ReviewRating.EASY, 0L);
        only("flashcard.review.accepted");
        assertEquals(1, commits);
    }

    @Test
    void failedSessionMutationsNeverClaimCreationOrAbandonment() {
        commit = () -> Uni.createFrom().failure(new IllegalStateException("payload-sentinel"));
        assertThrows(IllegalStateException.class, () -> call(sessions, "create",
                new Class<?>[]{String.class, FlashcardSessionCreateRequest.class},
                OWNER, new FlashcardSessionCreateRequest(7L, FlashcardSessionMode.ALL, 20, true)));
        assertNo("flashcard.session.creation.completed");
        only("flashcard.session.creation.failed");
        records.clear();
        assertThrows(IllegalStateException.class, () -> call(sessions, "abandon",
                new Class<?>[]{String.class, Long.class}, OWNER, 10L));
        assertNo("flashcard.session.abandon.completed");
        only("flashcard.session.abandon.failed");
    }

    @Test
    void historyWriteFailureDoesNotLogProgressAsPersisted() {
        reviewRows.failure = new IllegalStateException("payload-sentinel");
        assertThrows(IllegalStateException.class, () -> review(reviews(), ReviewRating.GOOD, 0L));
        assertNo("flashcard.review.accepted");
        only("flashcard.review.failed");
        assertEquals(0, commits);
        assertEquals(1, rollbacks);
    }

    @Test
    void detailRetrievalIncludesIdentifiersWithoutVocabularyContent() throws Throwable {
        var cards = new FlashcardService(new CardRows());
        call(cards, "getFlashcard", new Class<?>[]{Long.class}, 42L);
        assertEquals("42", only("flashcard.catalogue.detail.completed").getMdc("vocabularyId"));
        records.clear();
        call(new PersonalFlashcardService(cards, statistics), "detail", new Class<?>[]{String.class, Long.class}, OWNER, 42L);
        assertEquals("42", only("flashcard.personal.detail.completed").getMdc("vocabularyId"));
        assertNo("flashcard.catalogue.detail.completed");
    }

    @Test
    void emptyStatisticsAndInvalidPaginationHaveCorrectOutcomes() throws Throwable {
        catalogue.emptyStatistics = true;
        call(statistics, "statistics", new Class<?>[]{String.class, String.class, Long.class}, OWNER, null, null);
        assertTrue(only("flashcard.statistics.completed").getFormattedMessage().contains("studied percentage=0.00"));
        records.clear();
        assertThrows(com.japaneselearning.common.exception.ValidationException.class,
                () -> call(statistics, "list", new Class<?>[]{String.class, String.class, Long.class,
                                FlashcardLearningRepository.Collection.class, int.class, int.class},
                        OWNER, null, null, FlashcardLearningRepository.Collection.DUE, -1, 20));
        only("flashcard.collection.rejected");
        assertNo("flashcard.collection.completed");
    }

    @Test
    void cancellationAndResubscriptionDoNotLeakCompletionMetadata() throws Exception {
        var emitter = new AtomicReference<UniEmitter<? super List<Vocabulary>>>();
        var repository = new CardRows() {
            public Uni<List<Vocabulary>> findVocabulary(String level, Integer lesson, int offset, int size) {
                return Uni.createFrom().<List<Vocabulary>>emitter(emitter::set);
            }
        };
        var pipeline = observe(new FlashcardService(repository), "getFlashcards",
                new Class<?>[]{String.class, Integer.class, int.class, int.class}, new Object[]{"N5", null, 0, 20});
        assertTrue(records.isEmpty(), "Pipeline assembly must not log success or start");
        MDC.put("correlationId", "flashcard-request-42");
        MDC.put("traceId", "0123456789abcdef0123456789abcdef");
        try {
            var cancelled = pipeline.subscribe().with(ignored -> fail("Cancelled retrieval completed"));
            assertNo("flashcard.catalogue.list.completed");
            cancelled.cancel();
            only("flashcard.catalogue.list.cancelled");
            assertNo("flashcard.catalogue.list.completed");
            for (int subscription = 0; subscription < 2; subscription++) {
                records.clear();
                var result = pipeline.subscribeAsCompletionStage();
                assertFalse(result.isDone());
                assertNo("flashcard.catalogue.list.completed");
                emitter.get().complete(List.of());
                assertTrue(result.isDone());
                assertEquals("0", only("flashcard.catalogue.list.completed").getMdc("count"));
                assertEquals("3", only("flashcard.catalogue.list.completed").getMdc("total"));
                assertNull(MDC.get("event.name"));
            }
        } finally {
            MDC.clear();
        }
    }

    private FlashcardReviewService reviews() {
        return new FlashcardReviewService(sessions, sessionRows, reviewRows, progressRows, progress,
                new SpacedRepetitionService(600, 365, 5, 21), time, json);
    }

    private Object review(FlashcardReviewService service, ReviewRating rating, long version) throws Throwable {
        return call(service, "submit", new Class<?>[]{String.class, Long.class, Long.class, FlashcardReviewRequest.class},
                OWNER, 10L, 11L, new FlashcardReviewRequest(rating, version));
    }

    // Exercise actual service pipelines and the existing interceptor, replacing only database I/O.
    // The request-local session double separates work completion from commit completion; it is not a MySQL test.
    private Object call(Object target, String method, Class<?>[] types, Object... parameters) throws Throwable {
        return VertxContextSupport.subscribeAndAwait(() -> {
            var context = Vertx.currentContext();
            var key = new BaseKey<Mutiny.Session>(Mutiny.Session.class, ((Implementor) ClientProxy.unwrap(factory)).getUuid());
            var session = (Mutiny.Session) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{Mutiny.Session.class},
                    (proxy, invoked, args) -> switch (invoked.getName()) {
                        case "isOpen" -> true;
                        case "withTransaction" -> {
                            @SuppressWarnings("unchecked")
                            var work = (Function<Mutiny.Transaction, Uni<Object>>) args[0];
                            yield Uni.createFrom().deferred(() -> work.apply(null))
                                    .call(() -> {
                                        assertTrue(records.stream().noneMatch(r -> "success".equals(r.getMdc("outcome"))
                                                        && r.getLevel().intValue() >= java.util.logging.Level.INFO.intValue()),
                                                "Business success must wait for transaction completion");
                                        return commit.get();
                                    })
                                    .invoke(() -> commits++)
                                    .onFailure().invoke(() -> rollbacks++);
                        }
                        default -> throw new UnsupportedOperationException(invoked.getName());
                    });
            context.putLocal(key, session);
            MDC.put("correlationId", "flashcard-request-42");
            MDC.put("traceId", "0123456789abcdef0123456789abcdef");
            MDC.put("spanId", "0123456789abcdef");
            try {
                return observe(target, method, types, parameters)
                        .eventually(() -> {
                            context.removeLocal(key);
                            MDC.clear();
                        });
            } catch (Exception failure) {
                context.removeLocal(key);
                MDC.clear();
                return Uni.createFrom().failure(failure);
            }
        });
    }

    private Uni<?> observe(Object target, String name, Class<?>[] types, Object[] parameters) throws Exception {
        var method = target.getClass().getMethod(name, types);
        var invocation = (InvocationContext) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{InvocationContext.class},
                (proxy, invoked, args) -> switch (invoked.getName()) {
                    case "getMethod" -> method;
                    case "getTarget" -> target;
                    case "getParameters" -> parameters;
                    case "proceed" -> {
                        try {
                            yield method.invoke(target, parameters);
                        } catch (InvocationTargetException failure) {
                            throw failure.getCause();
                        }
                    }
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
            if (failure.getCause() instanceof Exception exception) throw exception;
            throw new AssertionError(failure.getCause());
        }
    }

    private ExtLogRecord only(String name) {
        var matches = records.stream().filter(record -> name.equals(record.getMdc("event.name"))).toList();
        assertEquals(1, matches.size(), name + ": " + records.stream().map(r -> r.getMdc("event.name")).toList());
        var event = matches.get(0);
        assertNotEquals(name, event.getFormattedMessage());
        if (name.endsWith(".completed") || name.endsWith(".accepted") || name.endsWith(".replayed")) {
            assertEquals("flashcard-request-42", event.getMdc("correlationId"));
            assertEquals("0123456789abcdef0123456789abcdef", event.getMdc("traceId"));
        }
        return event;
    }

    private void assertNo(String name) {
        assertTrue(records.stream().noneMatch(record -> name.equals(record.getMdc("event.name"))), name);
    }

    private static VocabularyProgress progressRow(Instant due) {
        var row = new VocabularyProgress();
        row.vocabularyId = 42L;
        row.status = LearningStatus.LEARNING;
        row.easeFactor = 2.5;
        row.intervalSeconds = 86400;
        row.successfulRepetitionCount = 1;
        row.nextReviewAt = FlashcardTime.stored(due);
        row.lastReviewedAt = FlashcardTime.stored(NOW.minusSeconds(86400));
        return row;
    }

    private static class SessionRows extends FlashcardSessionRepository {
        FlashcardSession row = new FlashcardSession();
        List<FlashcardSessionItem> items = new ArrayList<>();
        int inserts;

        SessionRows() {
            row.id = 10L;
            row.lessonId = 7L;
            row.mode = FlashcardSessionMode.NEW_AND_DUE;
            row.status = FlashcardSessionStatus.IN_PROGRESS;
            row.itemCount = 1;
            var item = new FlashcardSessionItem();
            item.id = 11L;
            item.sessionId = 10L;
            item.vocabularyId = 42L;
            items.add(item);
        }

        public Uni<FlashcardSession> owned(String subject, Long id, boolean lock) {
            return Uni.createFrom().item(row);
        }

        public Uni<List<FlashcardSessionItem>> items(Long id) {
            return Uni.createFrom().item(items);
        }

        public Uni<FlashcardSession> persistAndFlush(FlashcardSession entity) {
            inserts++;
            entity.id = 10L;
            row = entity;
            return Uni.createFrom().item(entity);
        }

        public Uni<Void> persistItems(List<FlashcardSessionItem> entities) {
            items = entities;
            items.forEach(item -> item.id = 11L);
            return Uni.createFrom().voidItem();
        }

        public Uni<Void> flush() {
            return Uni.createFrom().voidItem();
        }

        public Uni<Long> count(String query, Map<String, Object> params) {
            return Uni.createFrom().item(3L);
        }

        @SuppressWarnings("unchecked")
        public PanacheQuery<FlashcardSession> find(String query, Map<String, Object> params) {
            return (PanacheQuery<FlashcardSession>) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[]{PanacheQuery.class},
                    (proxy, method, args) -> switch (method.getName()) {
                        case "page" -> proxy;
                        case "list" -> Uni.createFrom().item(List.of(row));
                        default -> throw new UnsupportedOperationException(method.getName());
                    });
        }
    }

    private static class Catalogue extends FlashcardLearningRepository {
        List<Long> ids = List.of(42L);
        List<Object[]> cards = List.<Object[]>of(new Object[]{42L, "payload-sentinel"});
        long total = 3;
        boolean emptyStatistics;

        public Uni<Void> validateScope(String level, Long lesson) {
            return Uni.createFrom().voidItem();
        }

        public Uni<List<Long>> select(String subject, Long lesson, FlashcardSessionMode mode, LocalDateTime now, int limit, boolean shuffle) {
            return Uni.createFrom().item(ids);
        }

        public Uni<List<Object[]>> cards(String subject, String level, Long lesson, Collection collection, LocalDateTime now, int page, int size) {
            return Uni.createFrom().item(cards);
        }

        public Uni<Long> countCards(String subject, String level, Long lesson, Collection collection, LocalDateTime now) {
            return Uni.createFrom().item(total);
        }

        public Uni<Object[]> progressCounts(String subject, String level, Long lesson, LocalDateTime now) {
            return Uni.createFrom().item(emptyStatistics ? new Object[]{0L, 0L, 0L, 0L, 0L, 0L}
                    : new Object[]{3L, 2L, 1L, 0L, 0L, 1L});
        }

        public Uni<Object[]> reviewCounts(String subject, String level, Long lesson) {
            return Uni.createFrom().item(emptyStatistics ? new Object[]{0L, 0L, 0L, 0L} : new Object[]{1L, 2L, 1L, 1L});
        }
    }

    private static class ProgressRows extends VocabularyProgressRepository {
        VocabularyProgress row;
        int writes;

        public Uni<VocabularyProgress> findOwned(String subject, Long id) {
            return Uni.createFrom().item(row);
        }

        public Uni<List<VocabularyProgress>> findOwned(String subject, List<Long> ids) {
            return Uni.createFrom().item(row == null ? List.of() : List.of(row));
        }

        public Uni<VocabularyProgress> persist(VocabularyProgress entity) {
            writes++;
            row = entity;
            return Uni.createFrom().item(entity);
        }
    }

    private static class ReviewRows extends FlashcardReviewRepository {
        FlashcardReview row;
        int writes;
        RuntimeException failure;

        public Uni<FlashcardReview> findItem(Long id) {
            return Uni.createFrom().item(row);
        }

        public Uni<FlashcardReview> persistAndFlush(FlashcardReview entity) {
            if (failure != null) return Uni.createFrom().failure(failure);
            writes++;
            entity.id = 12L;
            row = entity;
            return Uni.createFrom().item(entity);
        }
    }

    private static class BookmarkRows extends VocabularyBookmarkRepository {
        int affected = 1;
        long removed = 1;

        public Uni<Integer> add(String subject, Long id, LocalDateTime now) {
            return Uni.createFrom().item(affected);
        }

        public Uni<Long> remove(String subject, Long id) {
            return Uni.createFrom().item(removed);
        }

        public Uni<List<VocabularyBookmark>> findOwned(String subject, List<Long> ids) {
            return Uni.createFrom().item(List.of());
        }
    }

    private static class CardRows extends FlashcardRepository {
        public Uni<Vocabulary> findVocabularyById(Long id) {
            var row = new Vocabulary();
            row.id = id;
            row.word = "payload-sentinel";
            return Uni.createFrom().item(row);
        }

        public Uni<List<Vocabulary>> findVocabulary(String level, Integer lesson, int offset, int size) {
            return findVocabularyById(42L).map(List::of);
        }

        public Uni<Long> countVocabulary(String level, Integer lesson) {
            return Uni.createFrom().item(3L);
        }

        public Uni<List<VocabularyReading>> findReadings(Long id) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<VocabularyMeaning>> findMeanings(Long id) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<PartOfSpeech>> findPartsOfSpeech(Long id) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<LevelDetail>> findLevels(Long id) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<LessonDetail>> findLessons(Long id) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<KanjiDetail>> findKanji(Long id) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<VocabularyExample>> findExamples(Long id) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<VocabularyPitchAccent>> findPitchAccentsByReadingIds(List<Long> ids) {
            return Uni.createFrom().item(List.of());
        }

        public Uni<List<KanjiReading>> findKanjiReadings(List<Long> ids) {
            return Uni.createFrom().item(List.of());
        }
    }
}
