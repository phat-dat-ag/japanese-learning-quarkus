package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse;
import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse.Classification;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.vertx.mutiny.mysqlclient.MySQLPool;

import jakarta.inject.Inject;

import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizClassificationSnapshotMysqlTest {
    private static final String BASE = "/api/v1/kanji-quiz";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;

    @Test
    void levelCaptureFailureRollsBackSessionOptionsAndEarlierLessonCaptures() throws Exception {
        long level = level();
        long lesson = lesson(level);
        question(level, lesson);
        String jwt = token();
        List<Long> before = snapshotCounts();
        sql(
                "ALTER TABLE quiz_session_question_levels ADD CONSTRAINT"
                        + " ck_quiz_capture_test_failure CHECK (level_id <> "
                        + level
                        + ")");
        try {
            create(jwt, lesson)
                    .then()
                    .statusCode(409)
                    .body("error.code", equalTo("QUIZ_GAME_CONFLICT"));
            assertEquals(before, snapshotCounts());
        } finally {
            sql("ALTER TABLE quiz_session_question_levels DROP CHECK ck_quiz_capture_test_failure");
        }
        long session =
                create(jwt, lesson)
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data.sessionId");
        assertEquals(
                1,
                number(
                        "SELECT COUNT(*) FROM quiz_session_questions WHERE session_id="
                                + session
                                + " AND classifications_captured=TRUE"));
        assertEquals(
                1,
                number(
                        "SELECT COUNT(*) FROM quiz_session_question_lessons WHERE lesson_id="
                                + lesson));
        assertEquals(
                1,
                number(
                        "SELECT COUNT(*) FROM quiz_session_question_levels WHERE level_id="
                                + level));
    }

    @Test
    void movedLessonRetainsBothHistoricalLevelAssignmentsAcrossCompletedSessions()
            throws Exception {
        long firstLevel = level();
        long secondLevel = level();
        long lesson = lesson(firstLevel);
        question(firstLevel, lesson);
        String jwt = token();
        long first =
                create(jwt, lesson)
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data.sessionId");
        finish(jwt, first);
        sql("UPDATE lessons SET level_id=" + secondLevel + " WHERE id=" + lesson);
        long second =
                create(jwt, lesson)
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data.sessionId");
        finish(jwt, second);
        QuizProgressBreakdownResponse result =
                given().auth()
                        .oauth2(jwt)
                        .get(BASE + "/progress/breakdown")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getObject("data", QuizProgressBreakdownResponse.class);
        assertEquals(
                List.of(firstLevel, secondLevel),
                result.lessons().stream().map(b -> b.levelId()).toList());
        assertEquals(
                List.of(lesson, lesson), result.lessons().stream().map(b -> b.lessonId()).toList());
        result.lessons()
                .forEach(
                        bucket -> {
                            assertEquals(Classification.ASSIGNED, bucket.classification());
                            assertEquals(1, bucket.completedSessions());
                            assertEquals(1, bucket.answeredCount());
                            assertEquals(1, bucket.correctCount());
                        });
        assertEquals(
                List.of(firstLevel, secondLevel),
                result.levels().stream().map(b -> b.levelId()).toList());
        // The explicit first level remains assigned in both sessions; only the lesson-implied level
        // moves.
        assertEquals(
                List.of(2L, 1L), result.levels().stream().map(b -> b.answeredCount()).toList());
        assertEquals(
                List.of(2L, 1L), result.levels().stream().map(b -> b.completedSessions()).toList());
        given().auth()
                .oauth2(jwt)
                .get(BASE + "/progress")
                .then()
                .statusCode(200)
                .body("data.completedSessions", equalTo(2))
                .body("data.answeredCount", equalTo(2))
                .body("data.correctCount", equalTo(2));
    }

    @Test
    void captureIncludesClassificationCommittedAfterCandidateSelection() throws Exception {
        long firstLevel = level();
        long addedLevel = level();
        long lesson = lesson(firstLevel);
        long question = question(firstLevel, lesson);
        String jwt = token();
        var connection = pool.getConnection().await().atMost(TIMEOUT);
        var transaction = connection.begin().await().atMost(TIMEOUT);
        CompletableFuture<Response> pending = null;
        boolean committed = false;
        try {
            long connectionId =
                    connection
                            .query("SELECT CONNECTION_ID()")
                            .execute()
                            .await()
                            .atMost(TIMEOUT)
                            .iterator()
                            .next()
                            .getLong(0);
            connection
                    .query("SELECT id FROM quiz_questions WHERE id=" + question + " FOR UPDATE")
                    .execute()
                    .await()
                    .atMost(TIMEOUT);
            connection
                    .query(
                            "INSERT INTO quiz_question_levels(question_id,level_id) VALUES ("
                                    + question
                                    + ","
                                    + addedLevel
                                    + ")")
                    .execute()
                    .await()
                    .atMost(TIMEOUT);
            pending = CompletableFuture.supplyAsync(() -> create(jwt, lesson));
            assertTrue(
                    awaitQuestionLockWait(connectionId, question, pending),
                    "Creation must select candidates and then wait for the question lock");
            transaction.commit().await().atMost(TIMEOUT);
            committed = true;
            long session =
                    pending.get(15, TimeUnit.SECONDS)
                            .then()
                            .statusCode(200)
                            .extract()
                            .jsonPath()
                            .getLong("data.sessionId");
            List<Long> captured = new ArrayList<>();
            pool.query(
                            "SELECT c.level_id FROM quiz_session_question_levels c JOIN"
                                    + " quiz_session_questions q ON q.id=c.session_question_id WHERE"
                                    + " q.session_id="
                                    + session
                                    + " ORDER BY c.level_id")
                    .execute()
                    .await()
                    .atMost(TIMEOUT)
                    .forEach(row -> captured.add(row.getLong(0)));
            assertEquals(List.of(firstLevel, addedLevel), captured);
        } finally {
            try {
                if (!committed) transaction.rollback().await().atMost(TIMEOUT);
            } finally {
                connection.close().await().atMost(TIMEOUT);
                if (pending != null) pending.get(15, TimeUnit.SECONDS);
            }
        }
    }

    private boolean awaitQuestionLockWait(
            long connectionId, long questionId, CompletableFuture<Response> pending)
            throws InterruptedException {
        String query =
                """
                        SELECT COUNT(*) FROM performance_schema.data_lock_waits w
                        JOIN performance_schema.threads t ON t.THREAD_ID=w.BLOCKING_THREAD_ID
                        JOIN performance_schema.data_locks l
                            ON l.ENGINE=w.ENGINE AND l.ENGINE_LOCK_ID=w.REQUESTING_ENGINE_LOCK_ID
                        WHERE t.PROCESSLIST_ID=%d AND l.OBJECT_SCHEMA='vocabulary_import_test'
                            AND l.OBJECT_NAME='quiz_questions' AND l.INDEX_NAME='PRIMARY'
                            AND l.LOCK_TYPE='RECORD' AND l.LOCK_STATUS='WAITING' AND l.LOCK_DATA='%d'
                        """
                        .formatted(connectionId, questionId);
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        do {
            if (number(query) > 0) return true;
            if (pending.isDone()) return false;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        return false;
    }

    private Response create(String jwt, long lesson) {
        return given().auth()
                .oauth2(jwt)
                .contentType("application/json")
                .body(Map.of("lessonId", lesson, "questionCount", 1))
                .post(BASE + "/sessions");
    }

    private void finish(String jwt, long session) {
        long question = number("SELECT id FROM quiz_session_questions WHERE session_id=" + session);
        long option =
                number(
                        "SELECT id FROM quiz_session_options WHERE session_question_id="
                                + question
                                + " AND is_correct=1");
        given().auth()
                .oauth2(jwt)
                .contentType("application/json")
                .body(Map.of("sessionQuestionId", question, "selectedOptionId", option))
                .post(BASE + "/sessions/" + session + "/answers")
                .then()
                .statusCode(200);
        given().auth()
                .oauth2(jwt)
                .post(BASE + "/sessions/" + session + "/finish")
                .then()
                .statusCode(200);
    }

    private long question(long level, long lesson) {
        String reading = "\u3042" + UUID.randomUUID();
        sql(
                "INSERT INTO"
                        + " quiz_questions(source_type,sentence_reading,target_start,target_length,target_reading,status)"
                        + " VALUES ('CUSTOM','"
                        + reading
                        + "',0,1,'\u3042','PUBLISHED')");
        long id = number("SELECT id FROM quiz_questions WHERE sentence_reading='" + reading + "'");
        for (int i = 0; i < 4; i++) {
            sql(
                    "INSERT INTO quiz_question_options(question_id,option_text,is_correct) VALUES ("
                            + id
                            + ",'choice-"
                            + i
                            + "',"
                            + (i == 0 ? 1 : 0)
                            + ")");
        }
        sql(
                "INSERT INTO quiz_question_levels(question_id,level_id) VALUES ("
                        + id
                        + ","
                        + level
                        + ")");
        sql(
                "INSERT INTO quiz_question_lessons(question_id,lesson_id) VALUES ("
                        + id
                        + ","
                        + lesson
                        + ")");
        return id;
    }

    private long level() {
        String code = UUID.randomUUID().toString().substring(0, 8);
        long order = number("SELECT MAX(display_order) FROM jlpt_levels") + 1;
        sql(
                "INSERT INTO jlpt_levels(code,name,display_order) VALUES ('"
                        + code
                        + "','Capture test',"
                        + order
                        + ")");
        return number("SELECT id FROM jlpt_levels WHERE code='" + code + "'");
    }

    private long lesson(long level) {
        sql(
                "INSERT INTO lessons(level_id,lesson_number,title,display_order) VALUES ("
                        + level
                        + ",1,'Capture test',1)");
        return number("SELECT id FROM lessons WHERE level_id=" + level);
    }

    private List<Long> snapshotCounts() {
        return List.of(
                        "quiz_sessions",
                        "quiz_session_questions",
                        "quiz_session_options",
                        "quiz_session_question_lessons",
                        "quiz_session_question_levels")
                .stream()
                .map(table -> number("SELECT COUNT(*) FROM " + table))
                .toList();
    }

    private String token() throws Exception {
        var tokens = new JwtTestTokens(signingKey);
        var claims = tokens.claims("User");
        claims.setSubject(UUID.randomUUID().toString());
        return tokens.sign(claims);
    }

    private void sql(String query) {
        pool.query(query).execute().await().atMost(TIMEOUT);
    }

    private long number(String query) {
        return pool.query(query).execute().await().atMost(TIMEOUT).iterator().next().getLong(0);
    }
}
