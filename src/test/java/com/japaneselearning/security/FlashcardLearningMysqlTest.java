package com.japaneselearning.security;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.flashcard.domain.FlashcardSessionMode;
import com.japaneselearning.flashcard.domain.FlashcardSessionStatus;
import com.japaneselearning.flashcard.domain.ReviewRating;
import com.japaneselearning.flashcard.dto.FlashcardReviewRequest;
import com.japaneselearning.flashcard.dto.FlashcardSessionCreateRequest;
import com.japaneselearning.flashcard.dto.FlashcardSessionResponse;
import com.japaneselearning.flashcard.repository.FlashcardLearningRepository;
import com.japaneselearning.flashcard.service.FlashcardReviewService;
import com.japaneselearning.flashcard.service.FlashcardSessionService;
import com.japaneselearning.flashcard.service.LearningStatisticsService;
import com.japaneselearning.flashcard.service.VocabularyBookmarkService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(FlashcardMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class FlashcardLearningMysqlTest {
    @Inject
    MySQLPool pool;
    @Inject
    CoordinatedFlashcardProgressRepository coordinatedProgress;

    @org.junit.jupiter.api.AfterEach
    void releaseReadBarrier() {
        coordinatedProgress.reset();
    }

    @Inject
    com.japaneselearning.flashcard.repository.VocabularyProgressRepository progressRepository;
    @Inject
    FlashcardLearningRepository catalogue;
    org.jose4j.jwk.RsaJsonWebKey signingKey;
    @Inject
    FlashcardSessionService sessions;
    @Inject
    FlashcardReviewService reviews;
    @Inject
    VocabularyBookmarkService bookmarks;
    @Inject
    LearningStatisticsService statistics;

    @Test
    void persistsOrderResumesCompletesAndReplaysWithoutDoubleCounting() {
        var fixture = fixture(2);
        var session = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        assertEquals(session.items(), run(() -> sessions.get(fixture.owner, session.sessionId())).items());
        assertEquals(0, number("select count(*) from user_vocabulary_progress where user_subject='" + fixture.owner + "'"));
        var first = session.items().get(0);
        var accepted = run(() -> reviews.submit(fixture.owner, session.sessionId(), first.itemId(), request(ReviewRating.GOOD, 0)));
        assertEquals(1, accepted.progress().version());
        assertEquals(accepted, run(() -> reviews.submit(fixture.owner, session.sessionId(), first.itemId(), request(ReviewRating.GOOD, 0))));
        assertThrows(ConflictException.class, () -> run(() -> reviews.submit(fixture.owner, session.sessionId(), first.itemId(), request(ReviewRating.EASY, 0))));
        assertThrows(ConflictException.class, () -> run(() -> sessions.finish(fixture.owner, session.sessionId())));
        var second = session.items().get(1);
        run(() -> reviews.submit(fixture.owner, session.sessionId(), second.itemId(), request(ReviewRating.AGAIN, 0)));
        var finished = run(() -> sessions.finish(fixture.owner, session.sessionId()));
        assertEquals(FlashcardSessionStatus.COMPLETED, finished.status());
        assertEquals(2, finished.currentPosition());
        assertEquals(accepted, run(() -> reviews.submit(fixture.owner, session.sessionId(), first.itemId(), request(ReviewRating.GOOD, 0))));
        var counts = run(() -> statistics.statistics(fixture.owner, null, fixture.lesson));
        assertEquals(2, counts.totalReviewAttempts());
        assertEquals(1, counts.forgottenCount());
        assertEquals(1, counts.rememberedCount());
        assertEquals("100.00", counts.studiedPercentage().toString());
        assertEquals("0.00", counts.masteryPercentage().toString());
    }

    @Test
    void practiceDoesNotCreateOrChangeProgressAndEmptyDueIsNormal() {
        var fixture = fixture(1);
        var empty = run(() -> sessions.create(fixture.owner, new FlashcardSessionCreateRequest(fixture.lesson, FlashcardSessionMode.DUE, 20, false)));
        assertEquals("NO_ELIGIBLE_CARDS", empty.outcome());
        assertNull(empty.session());
        assertEquals(0, number("select count(*) from flashcard_sessions where user_subject='" + fixture.owner + "'"));
        var practice = create(fixture, FlashcardSessionMode.ALL);
        var result = run(() -> reviews.submit(fixture.owner, practice.sessionId(), practice.items().get(0).itemId(), request(ReviewRating.AGAIN, 0)));
        assertFalse(result.scheduling());
        assertEquals(0, result.progress().version());
        var counts = run(() -> statistics.statistics(fixture.owner, "N5", fixture.lesson));
        assertEquals(1, counts.newCount());
        assertEquals(1, counts.studiedCount());
        assertEquals(1, counts.practiceReviewAttempts());
        var scheduled = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        var learned = run(() -> reviews.submit(fixture.owner, scheduled.sessionId(), scheduled.items().get(0).itemId(), request(ReviewRating.GOOD, 0)));
        var revisit = create(fixture, FlashcardSessionMode.ALL);
        var unchanged = run(() -> reviews.submit(fixture.owner, revisit.sessionId(), revisit.items().get(0).itemId(), request(ReviewRating.EASY, 1)));
        assertEquals(learned.progress(), unchanged.progress());
        assertEquals(0, run(() -> statistics.list(fixture.owner, null, fixture.lesson, FlashcardLearningRepository.Collection.DUE, 0, 20)).totalElements());
    }

    @Test
    void ownershipIsExactAndProgressIndependent() {
        var fixture = fixture(1);
        var session = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        for (String stranger : List.of(fixture.owner.toUpperCase(), fixture.owner + " ", "other")) {
            assertThrows(ResourceNotFoundException.class, () -> run(() -> sessions.get(stranger, session.sessionId())));
            assertThrows(ResourceNotFoundException.class, () -> run(() -> reviews.submit(stranger, session.sessionId(), session.items().get(0).itemId(), request(ReviewRating.GOOD, 0))));
        }
        assertThrows(ResourceNotFoundException.class, () -> run(() -> sessions.get(fixture.owner, Long.MAX_VALUE)));
        run(() -> reviews.submit(fixture.owner, session.sessionId(), session.items().get(0).itemId(), request(ReviewRating.GOOD, 0)));
        assertEquals(1, run(() -> statistics.statistics("other", null, fixture.lesson)).newCount());
        assertEquals(0, run(() -> statistics.statistics("other", null, fixture.lesson)).studiedCount());
    }

    @Test
    void concurrentDuplicateSubmissionIsIdempotent() throws Exception {
        var fixture = fixture(1);
        var session = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        var connection = pool.getConnection().await().atMost(Duration.ofSeconds(10));
        var transaction = connection.begin().await().atMost(Duration.ofSeconds(10));
        CompletableFuture<Object> first;
        CompletableFuture<Object> second;
        try {
            connection.query("select id from flashcard_sessions where id=" + session.sessionId() + " for update")
                    .execute().await().atMost(Duration.ofSeconds(10));
            var start = new CountDownLatch(0);
            Supplier<Uni<?>> submission = () -> reviews.submit(fixture.owner, session.sessionId(),
                    session.items().get(0).itemId(), request(ReviewRating.GOOD, 0));
            first = CompletableFuture.supplyAsync(() -> concurrent(start, submission));
            second = CompletableFuture.supplyAsync(() -> concurrent(start, submission));
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
            long waiters;
            do {
                waiters = number("select count(*) from performance_schema.data_lock_waits w "
                        + "join performance_schema.data_locks l on w.BLOCKING_ENGINE_LOCK_ID=l.ENGINE_LOCK_ID "
                        + "where l.OBJECT_NAME='flashcard_sessions' and l.LOCK_DATA='" + session.sessionId() + "'");
                if (waiters < 2) Thread.sleep(10);
            } while (waiters < 2 && System.nanoTime() < deadline);
            assertTrue(waiters >= 2, "Both HTTP-independent transactions must be waiting on the session row");
            assertFalse(first.isDone());
            assertFalse(second.isDone());
        } finally {
            transaction.rollback().await().atMost(Duration.ofSeconds(10));
            connection.close().await().atMost(Duration.ofSeconds(10));
        }
        var outcomes = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        assertEquals(outcomes.get(0), outcomes.get(1));
        assertEquals(1, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).totalReviewAttempts());
    }

    @Test
    void firstReviewRaceAcrossSessionsRollsBackLoser() throws Exception {
        var fixture = fixture(1);
        var one = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        var two = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        var barrier = coordinatedProgress.coordinate(fixture.owner);
        var outcomes = race(() -> reviews.submit(fixture.owner, one.sessionId(), one.items().get(0).itemId(), request(ReviewRating.GOOD, 0)),
                () -> reviews.submit(fixture.owner, two.sessionId(), two.items().get(0).itemId(), request(ReviewRating.GOOD, 0)));
        assertEquals(1, outcomes.stream().filter(ConflictException.class::isInstance).count(), outcomes.toString());
        assertEquals(List.of(-1L, -1L), barrier.observedVersions());
        assertEquals(1, number("select count(*) from user_vocabulary_progress where user_subject='" + fixture.owner + "'"));
        assertEquals(1, number("select count(*) from flashcard_session_items i join flashcard_sessions s on s.id=i.session_id where s.user_subject='" + fixture.owner + "' and i.completed_at is not null"));
        assertEquals(1, number("select sum(completed_count) from flashcard_sessions where user_subject='" + fixture.owner + "'"));
        assertEquals(1, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).totalReviewAttempts());
    }

    @Test
    void existingProgressRaceDoesNotLoseUpdates() throws Exception {
        var fixture = fixture(1);
        var first = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        run(() -> reviews.submit(fixture.owner, first.sessionId(), first.items().get(0).itemId(), request(ReviewRating.GOOD, 0)));
        sql("update user_vocabulary_progress set last_reviewed_at=UTC_TIMESTAMP(6)-interval 2 day, next_review_at=UTC_TIMESTAMP(6)-interval 1 day where user_subject='" + fixture.owner + "'");
        var one = create(fixture, FlashcardSessionMode.DUE);
        var two = create(fixture, FlashcardSessionMode.DUE);
        var barrier = coordinatedProgress.coordinate(fixture.owner);
        var outcomes = race(() -> reviews.submit(fixture.owner, one.sessionId(), one.items().get(0).itemId(), request(ReviewRating.GOOD, 1)),
                () -> reviews.submit(fixture.owner, two.sessionId(), two.items().get(0).itemId(), request(ReviewRating.EASY, 1)));
        assertEquals(1, outcomes.stream().filter(ConflictException.class::isInstance).count(), outcomes.toString());
        assertEquals(List.of(0L, 0L), barrier.observedVersions());
        assertEquals(2, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).totalReviewAttempts());
        assertEquals(2, number("select successful_repetition_count from user_vocabulary_progress where user_subject='" + fixture.owner + "'"));
        assertEquals(1, number("select version from user_vocabulary_progress where user_subject='" + fixture.owner + "'"));
    }

    @Test
    void bookmarksAreIndependentIdempotentAndIsolated() throws Exception {
        var fixture = fixture(1);
        var outcomes = race(() -> bookmarks.add(fixture.owner, fixture.vocabulary), () -> bookmarks.add(fixture.owner, fixture.vocabulary));
        assertEquals(outcomes.get(0), outcomes.get(1));
        assertEquals(1, run(() -> statistics.list(fixture.owner, null, fixture.lesson, FlashcardLearningRepository.Collection.BOOKMARKS, 0, 20)).totalElements());
        assertEquals(0, run(() -> statistics.list("other", null, fixture.lesson, FlashcardLearningRepository.Collection.BOOKMARKS, 0, 20)).totalElements());
        run(() -> bookmarks.remove("other", fixture.vocabulary));
        assertEquals(1, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).newCount());
        run(() -> bookmarks.remove(fixture.owner, fixture.vocabulary));
        run(() -> bookmarks.remove(fixture.owner, fixture.vocabulary));
        assertEquals(0, run(() -> statistics.list(fixture.owner, null, fixture.lesson, FlashcardLearningRepository.Collection.BOOKMARKS, 0, 20)).totalElements());
    }


    @Test
    void httpLifecyclePreservesCompatibilityAndEnforcesOwnerEvenForAdmin() throws Exception {
        var fixture = fixture(1);
        String token = token("User", fixture.owner);
        String base = "/api/v1/flashcards";
        io.restassured.RestAssured.given().auth().oauth2(token).get(base + "/" + fixture.vocabulary)
                .then().statusCode(200).body("data.progress", org.hamcrest.Matchers.nullValue());
        io.restassured.RestAssured.given().auth().oauth2(token).put(base + "/" + fixture.vocabulary + "/bookmark")
                .then().statusCode(200).body("data.bookmarked", org.hamcrest.Matchers.equalTo(true));
        io.restassured.RestAssured.given().auth().oauth2(token).queryParam("includeLearning", true)
                .get(base + "/" + fixture.vocabulary).then().statusCode(200)
                .header("Cache-Control", "private, no-store")
                .body("data.progress.status", org.hamcrest.Matchers.equalTo("NEW"))
                .body("data.bookmarked", org.hamcrest.Matchers.equalTo(true));

        long lessonNumber = number("select lesson_number from lessons where id=" + fixture.lesson);
        io.restassured.RestAssured.given().auth().oauth2(token).queryParam("includeLearning", true)
                .queryParam("level", "N5").queryParam("lesson", lessonNumber).get(base)
                .then().statusCode(200).body("data.flashcardItems.size()", org.hamcrest.Matchers.equalTo(1))
                .body("data.flashcardItems[0].progress.status", org.hamcrest.Matchers.equalTo("NEW"))
                .body("data.flashcardItems[0].bookmarked", org.hamcrest.Matchers.equalTo(true));

        long sessionId = io.restassured.RestAssured.given().auth().oauth2(token).contentType("application/json")
                .body(java.util.Map.of("lessonId", fixture.lesson)).post(base + "/sessions")
                .then().statusCode(200).body("data.outcome", org.hamcrest.Matchers.equalTo("CREATED"))
                .extract().jsonPath().getLong("data.session.sessionId");
        var resumed = io.restassured.RestAssured.given().auth().oauth2(token).get(base + "/sessions/" + sessionId)
                .then().statusCode(200).extract().jsonPath();
        long item = resumed.getLong("data.items[0].itemId");
        io.restassured.RestAssured.given().auth().oauth2(token("Admin", fixture.owner + "-foreign"))
                .get(base + "/sessions/" + sessionId).then().statusCode(404);
        var body = java.util.Map.of("rating", "GOOD", "expectedProgressVersion", 0);
        var accepted = io.restassured.RestAssured.given().auth().oauth2(token).contentType("application/json").body(body)
                .put(base + "/sessions/" + sessionId + "/items/" + item + "/review")
                .then().statusCode(200).body("data.progress.version", org.hamcrest.Matchers.equalTo(1)).extract().jsonPath().getMap("data");
        var replay = io.restassured.RestAssured.given().auth().oauth2(token).contentType("application/json").body(body)
                .put(base + "/sessions/" + sessionId + "/items/" + item + "/review")
                .then().statusCode(200).extract().jsonPath().getMap("data");
        assertEquals(accepted, replay);
        io.restassured.RestAssured.given().auth().oauth2(token).queryParam("lessonId", fixture.lesson)
                .get(base + "/statistics").then().statusCode(200)
                .body("data.studiedCount", org.hamcrest.Matchers.equalTo(1));
        io.restassured.RestAssured.given().auth().oauth2(token).queryParam("lessonId", fixture.lesson)
                .get(base + "/reviews/due").then().statusCode(200)
                .body("data.totalElements", org.hamcrest.Matchers.equalTo(0));
        io.restassured.RestAssured.given().auth().oauth2(token).contentType("application/json")
                .body(java.util.Map.of("lessonId", fixture.lesson, "mode", "DUE")).post(base + "/sessions")
                .then().statusCode(200).body("data.outcome", org.hamcrest.Matchers.equalTo("NO_ELIGIBLE_CARDS"));
        io.restassured.RestAssured.given().auth().oauth2(token).queryParam("lessonId", fixture.lesson)
                .get(base + "/sessions").then().statusCode(200).body("data.totalElements", org.hamcrest.Matchers.equalTo(1));
        io.restassured.RestAssured.given().auth().oauth2(token).delete(base + "/" + fixture.vocabulary + "/bookmark")
                .then().statusCode(200).body("data.bookmarked", org.hamcrest.Matchers.equalTo(false));
    }

    @Test
    void statisticsDeduplicateOverlappingScopesAndCountUnseenVocabulary() {
        var fixture = fixture(2);
        var otherLesson = fixture(0);
        sql("insert into lesson_vocabulary(lesson_id,vocabulary_id,display_order) values(" + otherLesson.lesson + "," + fixture.vocabulary + ",0)");
        long n4 = number("select id from jlpt_levels where code='N4'");
        sql("insert into vocabulary_levels(vocabulary_id,level_id,display_order) values(" + fixture.vocabulary + "," + n4 + ",0)");
        var before = run(() -> statistics.statistics(fixture.owner, null, fixture.lesson));
        assertEquals(2, before.newCount());
        assertEquals("0.00", before.studiedPercentage().toString());
        var practice = run(() -> sessions.create(fixture.owner, new FlashcardSessionCreateRequest(otherLesson.lesson, FlashcardSessionMode.ALL, 20, false))).session();
        run(() -> reviews.submit(fixture.owner, practice.sessionId(), practice.items().get(0).itemId(), request(ReviewRating.AGAIN, 0)));
        var partial = run(() -> statistics.statistics(fixture.owner, null, fixture.lesson));
        assertEquals(2, partial.totalVocabularyCount());
        assertEquals(1, partial.studiedCount());
        assertEquals("50.00", partial.studiedPercentage().toString());
        assertEquals(1, run(() -> statistics.statistics(fixture.owner, "N5", null)).totalReviewAttempts());
        assertEquals(1, run(() -> statistics.statistics(fixture.owner, "N4", null)).totalReviewAttempts());
        var overall = run(() -> statistics.statistics(fixture.owner, null, null));
        assertEquals(1, overall.totalReviewAttempts());
        assertEquals(number("select count(*) from vocabulary"), overall.totalVocabularyCount());
        assertEquals(1, run(() -> statistics.list(fixture.owner, null, fixture.lesson, FlashcardLearningRepository.Collection.FORGOTTEN, 0, 20)).totalElements());
        seedMastered(fixture.owner, fixture.vocabulary, "2026-10-24 00:00:00");
        var mastered = run(() -> statistics.statistics(fixture.owner, null, otherLesson.lesson));
        assertEquals(1, mastered.masteredCount());
        assertEquals("100.00", mastered.masteryPercentage().toString());
        var empty = fixture(0);
        assertEquals("0.00", run(() -> statistics.statistics(fixture.owner, null, empty.lesson)).masteryPercentage().toString());
    }

    @Test
    void dueBoundaryIsInclusiveSortedAndPaginatedIncludingMastered() {
        var fixture = fixture(3);
        var selected = create(fixture, FlashcardSessionMode.ALL);
        List<Long> ids = selected.items().stream().map(FlashcardSessionResponse.Item::vocabularyId).toList();
        seedMastered(fixture.owner, ids.get(0), "2026-10-24 00:00:00");
        seedMastered(fixture.owner, ids.get(1), "2026-10-24 00:00:01");
        seedMastered(fixture.owner, ids.get(2), "2026-10-24 00:00:02");
        var now = java.time.LocalDateTime.parse("2026-10-24T00:00:01");
        var first = run(() -> io.quarkus.hibernate.reactive.panache.Panache.withTransaction(() ->
                catalogue.cards(fixture.owner, "N5", fixture.lesson, FlashcardLearningRepository.Collection.DUE, now, 0, 1)));
        var second = run(() -> io.quarkus.hibernate.reactive.panache.Panache.withTransaction(() ->
                catalogue.cards(fixture.owner, "N5", fixture.lesson, FlashcardLearningRepository.Collection.DUE, now, 1, 1)));
        assertEquals(ids.get(0), first.get(0)[0]);
        assertEquals(ids.get(1), second.get(0)[0]);
        assertEquals(2, run(() -> io.quarkus.hibernate.reactive.panache.Panache.withTransaction(() ->
                catalogue.countCards(fixture.owner, null, fixture.lesson, FlashcardLearningRepository.Collection.DUE, now))));
    }

    @Test
    void historyFailureRollsBackProgressAndSessionTogether() {
        var fixture = fixture(1);
        var session = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        String trigger = "flashcard_failure_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE TRIGGER " + trigger + " BEFORE INSERT ON flashcard_review_history FOR EACH ROW BEGIN "
                + "IF NEW.user_subject = '" + fixture.owner + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test history failure'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> run(() -> reviews.submit(fixture.owner, session.sessionId(),
                    session.items().get(0).itemId(), request(ReviewRating.GOOD, 0))));
            assertEquals(0, number("select count(*) from user_vocabulary_progress where user_subject='" + fixture.owner + "'"));
            assertEquals(0, number("select count(*) from flashcard_review_history where user_subject='" + fixture.owner + "'"));
            assertEquals(0, run(() -> sessions.get(fixture.owner, session.sessionId())).completedCount());
        } finally {
            sql("DROP TRIGGER " + trigger);
        }
    }

    @Test
    void practiceSamplesWholeLargeLessonAndRotatesUnshuffledCards() {
        var fixture = fixture(150);
        var ordered = run(() -> sessions.create(fixture.owner,
                new FlashcardSessionCreateRequest(fixture.lesson, FlashcardSessionMode.ALL, 100, false))).session();
        var originalIds = ordered.items().stream().map(FlashcardSessionResponse.Item::vocabularyId)
                .collect(java.util.stream.Collectors.toSet());
        var sampled = run(() -> sessions.create(fixture.owner,
                new FlashcardSessionCreateRequest(fixture.lesson, FlashcardSessionMode.ALL, 100, true))).session();
        assertEquals(100, sampled.items().size());
        assertEquals(100, sampled.items().stream().map(FlashcardSessionResponse.Item::vocabularyId).distinct().count());
        assertTrue(sampled.items().stream().anyMatch(item -> !originalIds.contains(item.vocabularyId())),
                "Sampling must include the full 150-card lesson, not shuffle a fixed first 100");
        assertEquals(sampled.items(), run(() -> sessions.get(fixture.owner, sampled.sessionId())).items());
        for (var item : ordered.items()) {
            run(() -> reviews.submit(fixture.owner, ordered.sessionId(), item.itemId(), request(ReviewRating.GOOD, 0)));
        }
        var next = run(() -> sessions.create(fixture.owner,
                new FlashcardSessionCreateRequest(fixture.lesson, FlashcardSessionMode.ALL, 100, false))).session();
        assertTrue(next.items().subList(0, 50).stream().noneMatch(item -> originalIds.contains(item.vocabularyId())));
        var covered = new java.util.HashSet<>(originalIds);
        next.items().forEach(item -> covered.add(item.vocabularyId()));
        assertEquals(150, covered.size());
        assertEquals(150, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).newCount());
    }

    @Test
    void overlappingSessionsCanBeAbandonedWithoutLosingCommittedLearning() throws Exception {
        var fixture = fixture(2);
        var one = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        var two = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        var accepted = run(() -> reviews.submit(fixture.owner, one.sessionId(), one.items().get(0).itemId(),
                request(ReviewRating.GOOD, 0)));
        var conflict = assertThrows(ConflictException.class, () -> run(() -> reviews.submit(fixture.owner,
                two.sessionId(), two.items().get(0).itemId(), request(ReviewRating.GOOD, 0))));
        assertEquals("FLASHCARD_NOT_DUE", conflict.getCode());
        String path = "/api/v1/flashcards/sessions/" + one.sessionId() + "/abandon";
        io.restassured.RestAssured.given().auth().oauth2(token("Admin", "foreign")).post(path).then().statusCode(404);
        io.restassured.RestAssured.given().auth().oauth2(token("User", fixture.owner)).post(path).then().statusCode(200)
                .body("data.status", org.hamcrest.Matchers.equalTo("ABANDONED"))
                .body("data.completedCount", org.hamcrest.Matchers.equalTo(1));
        var abandoned = run(() -> sessions.get(fixture.owner, one.sessionId()));
        assertNotNull(abandoned.abandonedAt());
        assertNull(abandoned.completedAt());
        assertEquals(abandoned, run(() -> sessions.abandon(fixture.owner, one.sessionId())));
        assertEquals(accepted, run(() -> reviews.submit(fixture.owner, one.sessionId(), one.items().get(0).itemId(),
                request(ReviewRating.GOOD, 0))));
        assertThrows(ConflictException.class, () -> run(() -> reviews.submit(fixture.owner, one.sessionId(),
                one.items().get(1).itemId(), request(ReviewRating.GOOD, 0))));
        run(() -> sessions.abandon(fixture.owner, two.sessionId()));
        var recovered = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        assertEquals(1, recovered.itemCount());
        assertEquals(one.items().get(1).vocabularyId(), recovered.items().get(0).vocabularyId());
        run(() -> reviews.submit(fixture.owner, recovered.sessionId(), recovered.items().get(0).itemId(),
                request(ReviewRating.GOOD, 0)));
        assertEquals(FlashcardSessionStatus.COMPLETED, run(() -> sessions.abandon(fixture.owner, recovered.sessionId())).status());
        assertEquals(2, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).totalReviewAttempts());
        assertEquals(2, run(() -> sessions.list(fixture.owner, fixture.lesson, FlashcardSessionStatus.ABANDONED, 0, 20)).totalElements());
    }

    @Test
    void abandonmentAndReviewSerializeWithoutPartialWrites() throws Exception {
        var fixture = fixture(2);
        var session = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        race(() -> sessions.abandon(fixture.owner, session.sessionId()), () -> reviews.submit(fixture.owner,
                session.sessionId(), session.items().get(0).itemId(), request(ReviewRating.GOOD, 0)));
        var state = run(() -> sessions.get(fixture.owner, session.sessionId()));
        assertEquals(FlashcardSessionStatus.ABANDONED, state.status());
        long attempts = run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).totalReviewAttempts();
        assertEquals(attempts, state.completedCount());
        assertEquals(attempts, state.items().stream().filter(item -> item.completedAt() != null).count());
        assertEquals(attempts, number("select count(*) from user_vocabulary_progress where user_subject='" + fixture.owner + "'"));
    }

    @Test
    void historyFailureAlsoRollsBackAnExistingProgressUpdate() {
        var fixture = fixture(1);
        var first = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        run(() -> reviews.submit(fixture.owner, first.sessionId(), first.items().get(0).itemId(), request(ReviewRating.GOOD, 0)));
        sql("update user_vocabulary_progress set last_reviewed_at=UTC_TIMESTAMP(6)-interval 2 day, "
                + "next_review_at=UTC_TIMESTAMP(6)-interval 1 day where user_subject='" + fixture.owner + "'");
        var due = create(fixture, FlashcardSessionMode.DUE);
        var before = due.items().get(0).progress();
        String trigger = "flashcard_failure_" + UUID.randomUUID().toString().replace("-", "");
        sql("CREATE TRIGGER " + trigger + " BEFORE INSERT ON flashcard_review_history FOR EACH ROW BEGIN "
                + "IF NEW.user_subject = '" + fixture.owner + "' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test existing history failure'; END IF; END");
        try {
            assertThrows(RuntimeException.class, () -> run(() -> reviews.submit(fixture.owner, due.sessionId(),
                    due.items().get(0).itemId(), request(ReviewRating.AGAIN, before.version()))));
            var restored = run(() -> sessions.get(fixture.owner, due.sessionId()));
            assertEquals(before, restored.items().get(0).progress());
            assertNull(restored.items().get(0).completedAt());
            assertEquals(0, restored.completedCount());
            assertEquals(FlashcardSessionStatus.IN_PROGRESS, restored.status());
            assertEquals(1, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).totalReviewAttempts());
        } finally {
            sql("DROP TRIGGER " + trigger);
        }
    }

    private void seedMastered(String owner, long vocabulary, String due) {
        sql("insert into user_vocabulary_progress(user_subject,vocabulary_id,status,successful_repetition_count,lapse_count,interval_seconds,ease_factor,last_reviewed_at,next_review_at,mastered_at,created_at,updated_at)"
                + " values('" + owner + "'," + vocabulary + ",'MASTERED',5,0,1814400,2.5,'2026-10-01 00:00:00','" + due + "','2026-10-01 00:00:00','2026-10-01 00:00:00','2026-10-01 00:00:00')");
    }

    private String token(String role, String subject) throws Exception {
        var tokens = new JwtTestTokens(signingKey);
        var claims = tokens.claims(role);
        claims.setSubject(subject);
        return tokens.sign(claims);
    }

    @Test
    void rejectsWrongItemOrderAndStaleRevisionWithoutWrites() {
        var fixture = fixture(2);
        var session = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        assertThrows(ConflictException.class, () -> run(() -> reviews.submit(fixture.owner, session.sessionId(),
                session.items().get(1).itemId(), request(ReviewRating.GOOD, 0))));
        assertThrows(ResourceNotFoundException.class, () -> run(() -> reviews.submit(fixture.owner, session.sessionId(),
                Long.MAX_VALUE, request(ReviewRating.GOOD, 0))));
        assertThrows(ConflictException.class, () -> run(() -> reviews.submit(fixture.owner, session.sessionId(),
                session.items().get(0).itemId(), request(ReviewRating.GOOD, 1))));
        assertEquals(0, run(() -> sessions.get(fixture.owner, session.sessionId())).completedCount());
        assertEquals(0, run(() -> statistics.statistics(fixture.owner, null, fixture.lesson)).totalReviewAttempts());
    }

    @Test
    void hibernateVersionCheckRejectsAStaleManagedProgressRow() {
        var fixture = fixture(1);
        var session = create(fixture, FlashcardSessionMode.NEW_AND_DUE);
        run(() -> reviews.submit(fixture.owner, session.sessionId(), session.items().get(0).itemId(), request(ReviewRating.GOOD, 0)));
        RuntimeException failure = assertThrows(RuntimeException.class, () -> run(() ->
                io.quarkus.hibernate.reactive.panache.Panache.withTransaction(() -> progressRepository.findOwned(fixture.owner, fixture.vocabulary)
                        .chain(row -> pool.query("update user_vocabulary_progress set version=version+1 where id=" + row.id).execute()
                                .invoke(ignored -> row.successfulRepetitionCount++)
                                .chain(() -> progressRepository.flush())))));
        boolean optimistic = false;
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof jakarta.persistence.OptimisticLockException || cause instanceof org.hibernate.StaleStateException)
                optimistic = true;
        }
        assertTrue(optimistic, failure.toString());
        assertEquals(1, number("select successful_repetition_count from user_vocabulary_progress where user_subject='" + fixture.owner + "'"));
    }

    private Fixture fixture(int count) {
        String owner = "flashcard-" + UUID.randomUUID();
        long level = number("select id from jlpt_levels where code='N5'");
        long order = number("select coalesce(max(lesson_number),0)+1 from lessons where level_id=" + level);
        sql("insert into lessons(level_id,lesson_number,title,display_order) values(" + level + "," + order + ",'" + owner + "'," + order + ")");
        long lesson = number("select id from lessons where title='" + owner + "'");
        long vocabulary = 0;
        for (int i = 0; i < count; i++) {
            String word = owner + "-" + i;
            sql("insert into vocabulary(word,normalized_word) values('" + word + "','" + word + "')");
            vocabulary = number("select id from vocabulary where normalized_word='" + word + "'");
            sql("insert into vocabulary_levels(vocabulary_id,level_id,display_order) values(" + vocabulary + "," + level + "," + i + ")");
            sql("insert into lesson_vocabulary(lesson_id,vocabulary_id,display_order) values(" + lesson + "," + vocabulary + "," + i + ")");
        }
        return new Fixture(owner, lesson, vocabulary);
    }

    private FlashcardSessionResponse create(Fixture fixture, FlashcardSessionMode mode) {
        return run(() -> sessions.create(fixture.owner, new FlashcardSessionCreateRequest(fixture.lesson, mode, 20, false))).session();
    }

    private FlashcardReviewRequest request(ReviewRating rating, long version) {
        return new FlashcardReviewRequest(rating, version);
    }

    private List<Object> race(Supplier<Uni<?>> one, Supplier<Uni<?>> two) throws Exception {
        var start = new CountDownLatch(1);
        var first = CompletableFuture.supplyAsync(() -> concurrent(start, one));
        var second = CompletableFuture.supplyAsync(() -> concurrent(start, two));
        start.countDown();
        return List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
    }

    private Object concurrent(CountDownLatch start, Supplier<Uni<?>> work) {
        try {
            if (!start.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("Race did not start");
            return VertxContextSupport.subscribeAndAwait(() -> work.get().map(item -> item));
        } catch (Exception failure) {
            return failure;
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    private <T> T run(Supplier<Uni<T>> work) {
        try {
            return VertxContextSupport.subscribeAndAwait(() -> work.get().map(item -> item));
        } catch (RuntimeException failure) {
            throw failure;
        } catch (Throwable failure) {
            throw new AssertionError(failure);
        }
    }

    private void sql(String query) {
        pool.query(query).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long number(String query) {
        return pool.query(query).execute().await().atMost(Duration.ofSeconds(10)).iterator().next().getLong(0);
    }

    private record Fixture(String owner, long lesson, long vocabulary) {
    }
}
