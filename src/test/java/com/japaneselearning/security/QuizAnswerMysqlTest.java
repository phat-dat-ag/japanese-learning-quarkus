package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.*;

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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizAnswerMysqlTest {
    private static final String BASE = "/api/v1/kanji-quiz/sessions/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;

    @Test
    void scoresCorrectAndIncorrectSnapshotsAndRejectsExhaustedSession() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 2);
        long second = snapshot(session, 9);
        long first = snapshot(session, 3);
        submit(token, session, first, option(first, true))
                .then()
                .statusCode(200)
                .body("data.correct", equalTo(true))
                .body("data.correctOptionId", equalTo((int) option(first, true)))
                .body("data.explanationVi", equalTo("snapshot vi"))
                .body("data.explanationEn", equalTo("snapshot en"))
                .body("data.score", equalTo(1))
                .body("data.answeredCount", equalTo(1))
                .body("data.remainingCount", equalTo(1));
        given().auth()
                .oauth2(token)
                .get(BASE + session + "/next")
                .then()
                .statusCode(200)
                .body("data.question.sessionQuestionId", equalTo((int) second));
        submit(token, session, second, option(second, false))
                .then()
                .statusCode(200)
                .body("data.correct", equalTo(false))
                .body("data.correctOptionId", equalTo((int) option(second, true)))
                .body("data.score", equalTo(1))
                .body("data.answeredCount", equalTo(2))
                .body("data.remainingCount", equalTo(0));
        assertProgress(token, session, 2, 1);
        submit(token, session, second, option(second, true)).then().statusCode(409);
        assertEquals(
                2,
                number(
                        "SELECT COUNT(*) FROM quiz_answers WHERE session_question_id IN ("
                                + first
                                + ","
                                + second
                                + ")"));
    }

    @Test
    void validatesOrderOptionMembershipAndDuplicatesWithoutAdvancing() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 2);
        long second = snapshot(session, 1);
        long first = snapshot(session, 0);
        long foreign = snapshot(session(owner, 1), 0);
        for (long question : List.of(second, foreign, Long.MAX_VALUE)) {
            submit(token, session, question, option(second, true))
                    .then()
                    .statusCode(409)
                    .body("error.code", equalTo("QUIZ_ANSWER_CONFLICT"));
        }
        for (long choice : List.of(option(second, true), option(foreign, true), Long.MAX_VALUE)) {
            submit(token, session, first, choice)
                    .then()
                    .statusCode(400)
                    .body("error.code", equalTo("QUIZ_ANSWER_INVALID"));
        }
        assertProgress(token, session, 0, 0);
        submit(token, session, first, option(first, true)).then().statusCode(200);
        submit(token, session, first, option(first, false)).then().statusCode(409);
        assertProgress(token, session, 1, 1);
    }

    @Test
    void enforcesSubjectOwnershipIncludingAdminsAndTerminalStatus() throws Exception {
        String owner = "Owner-" + UUID.randomUUID();
        long session = session(owner, 1);
        long question = snapshot(session, 0);
        long choice = option(question, true);
        for (String role : List.of("User", "Admin")) {
            for (String subject : List.of(owner.toLowerCase(), owner + " ", "stranger")) {
                String token = token(role, subject);
                Response foreign = submit(token, session, question, choice);
                Response missing = submit(token, Long.MAX_VALUE, question, choice);
                foreign.then()
                        .statusCode(404)
                        .body("error.code", equalTo("QUIZ_SESSION_NOT_FOUND"));
                missing.then().statusCode(404);
                assertEquals(
                        foreign.jsonPath().getMap("error"), missing.jsonPath().getMap("error"));
            }
        }
        for (String status : List.of("COMPLETED", "ABANDONED")) {
            sql(
                    "UPDATE quiz_sessions SET status='"
                            + status
                            + "', completed_at=UTC_TIMESTAMP(6) WHERE id="
                            + session);
            submit(token("Admin", owner), session, question, choice).then().statusCode(409);
        }
        assertProgress(token("User", owner), session, 0, 0);
    }

    @Test
    void validatesRequestAndSecurity() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 1);
        long question = snapshot(session, 0);
        long choice = option(question, true);
        given().contentType("application/json")
                .body(Map.of("sessionQuestionId", question, "selectedOptionId", choice))
                .post(BASE + session + "/answers")
                .then()
                .statusCode(401);
        submit(token("Guest", owner), session, question, choice).then().statusCode(403);
        submit(token("User", " "), session, question, choice).then().statusCode(401);
        for (String body :
                List.of(
                        "{}",
                        "null",
                        "{",
                        "{\"sessionQuestionId\":1}",
                        "{\"sessionQuestionId\":0,\"selectedOptionId\":1}",
                        "{\"sessionQuestionId\":1,\"selectedOptionId\":-1}")) {
            given().auth()
                    .oauth2(token)
                    .contentType("application/json")
                    .body(body)
                    .post(BASE + session + "/answers")
                    .then()
                    .statusCode(400);
        }
        submit(token, 0, question, choice).then().statusCode(400);
        assertProgress(token, session, 0, 0);
    }

    @Test
    void databaseFailureAfterInsertRollsBackAnswerAndScore() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 1);
        long question = snapshot(session, 0);
        sql(
                "CREATE TRIGGER quiz_answer_test_failure AFTER INSERT ON quiz_answers FOR EACH ROW"
                        + " BEGIN IF NEW.session_question_id="
                        + question
                        + " THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='forced answer failure';"
                        + " END IF; END");
        try {
            submit(token, session, question, option(question, true)).then().statusCode(500);
            assertProgress(token, session, 0, 0);
            assertEquals(
                    0,
                    number(
                            "SELECT COUNT(*) FROM quiz_answers WHERE session_question_id="
                                    + question));
        } finally {
            sql("DROP TRIGGER quiz_answer_test_failure");
        }
        submit(token, session, question, option(question, true)).then().statusCode(200);
        assertProgress(token, session, 1, 1);
    }

    @Test
    void concurrentRequestsWaitForSessionLockAndScoreOnlyOnce() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 2);
        long question = snapshot(session, 0);
        snapshot(session, 1);
        long correct = option(question, true);
        long wrong = option(question, false);
        var connection = pool.getConnection().await().atMost(TIMEOUT);
        var transaction = connection.begin().await().atMost(TIMEOUT);
        CompletableFuture<Response> first = null;
        CompletableFuture<Response> second = null;
        boolean committed = false;
        try {
            connection
                    .query("SELECT id FROM quiz_sessions WHERE id=" + session + " FOR UPDATE")
                    .execute()
                    .await()
                    .atMost(TIMEOUT);
            first = CompletableFuture.supplyAsync(() -> submit(token, session, question, correct));
            second = CompletableFuture.supplyAsync(() -> submit(token, session, question, wrong));
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            boolean waiting = false;
            do {
                waiting =
                        number(
                                "SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID)"
                                        + " FROM performance_schema.data_lock_waits w JOIN"
                                        + " performance_schema.data_locks l ON"
                                        + " l.ENGINE=w.ENGINE AND"
                                        + " l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE"
                                        + " l.OBJECT_SCHEMA=DATABASE() AND"
                                        + " l.OBJECT_NAME='quiz_sessions' AND l.LOCK_DATA='"
                                        + session
                                        + "'")
                                >= 2;
                if (waiting) break;
                Thread.sleep(50);
            } while (System.nanoTime() < deadline);
            assertTrue(waiting, "Both requests must overlap at the persisted session lock");
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            transaction.commit().await().atMost(TIMEOUT);
            committed = true;
            Response a = first.get(15, TimeUnit.SECONDS);
            Response b = second.get(15, TimeUnit.SECONDS);
            assertEquals(
                    List.of(200, 409),
                    java.util.stream.Stream.of(a.statusCode(), b.statusCode()).sorted().toList());
            Response winner = a.statusCode() == 200 ? a : b;
            long score = winner.jsonPath().getBoolean("data.correct") ? 1 : 0;
            assertProgress(token, session, 1, (int) score);
            assertEquals(
                    1,
                    number(
                            "SELECT COUNT(*) FROM quiz_answers WHERE session_question_id="
                                    + question));
        } finally {
            if (!committed) transaction.rollback().await().atMost(TIMEOUT);
            connection.close().await().atMost(TIMEOUT);
            if (first != null) first.get(15, TimeUnit.SECONDS);
            if (second != null) second.get(15, TimeUnit.SECONDS);
        }
    }

    private void assertProgress(String token, long session, int count, int score) {
        given().auth()
                .oauth2(token)
                .get(BASE + session)
                .then()
                .statusCode(200)
                .body("data.answeredCount", equalTo(count))
                .body("data.score", equalTo(score));
    }

    private Response submit(String token, long session, long question, long option) {
        return given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(Map.of("sessionQuestionId", question, "selectedOptionId", option))
                .post(BASE + session + "/answers");
    }

    private long session(String owner, int count) {
        sql(
                "INSERT INTO quiz_sessions(user_subject,question_count) VALUES ('"
                        + owner
                        + "',"
                        + count
                        + ")");
        return number("SELECT MAX(id) FROM quiz_sessions WHERE user_subject='" + owner + "'");
    }

    private long snapshot(long session, int order) {
        sql(
                "INSERT INTO"
                        + " quiz_session_questions(session_id,question_version,question_number,source_type,sentence_reading,target_start,target_length,target_reading,explanation_vi,explanation_en)"
                        + " VALUES ("
                        + session
                        + ",1,"
                        + order
                        + ",'CUSTOM','reading',0,7,'reading','snapshot vi','snapshot en')");
        long id =
                number(
                        "SELECT id FROM quiz_session_questions WHERE session_id="
                                + session
                                + " AND question_number="
                                + order);
        for (int i = 0; i < 4; i++) {
            sql(
                    "INSERT INTO quiz_session_options(session_question_id,option_text,is_correct)"
                            + " VALUES ("
                            + id
                            + ",'choice-"
                            + i
                            + "',"
                            + (i == 0 ? 1 : 0)
                            + ")");
        }
        return id;
    }

    private long option(long question, boolean correct) {
        return number(
                "SELECT MIN(id) FROM quiz_session_options WHERE session_question_id="
                        + question
                        + " AND is_correct="
                        + (correct ? 1 : 0));
    }

    private String token(String role, String subject) throws Exception {
        var tokens = new JwtTestTokens(signingKey);
        var claims = tokens.claims(role);
        claims.setSubject(subject);
        return tokens.sign(claims);
    }

    private void sql(String query) {
        pool.query(query).execute().await().atMost(TIMEOUT);
    }

    private long number(String query) {
        return pool.query(query).execute().await().atMost(TIMEOUT).iterator().next().getLong(0);
    }
}
