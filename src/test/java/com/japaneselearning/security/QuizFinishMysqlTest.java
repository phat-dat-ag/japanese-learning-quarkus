package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizFinishMysqlTest {
    private static final String BASE = "/api/v1/kanji-quiz/sessions/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;

    @Test
    void completionPersistsFinalTotalsAndTimeWithoutChangingSnapshotsOrAnswers() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 2);
        long first = snapshot(session, 0);
        long second = snapshot(session, 1);
        answer(token, session, first, true).then().statusCode(200);
        answer(token, session, second, false).then().statusCode(200);
        List<String> before = history(session);
        long progress = number("SELECT COUNT(*) FROM quiz_user_progress");
        Response response = finish(token, session);
        assertCompleted(response, session, 2, 1);
        assertEquals(
                Set.of(
                        "sessionId",
                        "status",
                        "questionCount",
                        "correctCount",
                        "incorrectCount",
                        "score",
                        "completedAt"),
                response.jsonPath().getMap("data").keySet());
        var persisted =
                pool.query("SELECT completed_at FROM quiz_sessions WHERE id=" + session)
                        .execute()
                        .await()
                        .atMost(TIMEOUT)
                        .iterator()
                        .next()
                        .getLocalDateTime(0);
        assertEquals(
                persisted, LocalDateTime.parse(response.jsonPath().getString("data.completedAt")));
        assertEquals(1, number("SELECT version FROM quiz_sessions WHERE id=" + session));
        assertEquals(before, history(session));
        assertEquals(progress, number("SELECT COUNT(*) FROM quiz_user_progress"));
        assertCompletedReads(token, session, 2, 1);
        finish(token, session)
                .then()
                .statusCode(409)
                .body("error.code", equalTo("QUIZ_FINISH_CONFLICT"));
        answer(token, session, second, true)
                .then()
                .statusCode(409)
                .body("error.code", equalTo("QUIZ_ANSWER_CONFLICT"));
        assertEquals(before, history(session));
        assertEquals(1, number("SELECT version FROM quiz_sessions WHERE id=" + session));
        assertEquals(
                persisted,
                pool.query("SELECT completed_at FROM quiz_sessions WHERE id=" + session)
                        .execute()
                        .await()
                        .atMost(TIMEOUT)
                        .iterator()
                        .next()
                        .getLocalDateTime(0));
    }

    @Test
    void prematureEmptyAndAbandonedSessionsCannotFinish() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long empty = session(owner, 1);
        finish(token, empty).then().statusCode(409);
        long session = session(owner, 2);
        long first = snapshot(session, 0);
        snapshot(session, 1);
        finish(token, session).then().statusCode(409);
        answer(token, session, first, true).then().statusCode(200);
        List<String> before = history(session);
        finish(token, session)
                .then()
                .statusCode(409)
                .body("error.code", equalTo("QUIZ_FINISH_CONFLICT"));
        assertEquals(
                1,
                number(
                        "SELECT COUNT(*) FROM quiz_sessions WHERE id="
                                + session
                                + " AND status='IN_PROGRESS' AND completed_at IS NULL AND"
                                + " version=0"));
        assertEquals(before, history(session));
        sql(
                "UPDATE quiz_sessions SET status='ABANDONED', completed_at=UTC_TIMESTAMP(6) WHERE"
                        + " id="
                        + session);
        finish(token, session).then().statusCode(409);
        assertEquals(before, history(session));
    }

    @Test
    void ownershipIsExactAndMissingAndForeignSessionsAreIndistinguishable() throws Exception {
        String owner = "Owner-" + UUID.randomUUID();
        long session = session(owner, 1);
        long question = snapshot(session, 0);
        for (String role : List.of("User", "Admin")) {
            for (String subject : List.of(owner.toLowerCase(), owner + " ", "stranger")) {
                String token = token(role, subject);
                Response foreign = finish(token, session);
                Response missing = finish(token, Long.MAX_VALUE);
                foreign.then()
                        .statusCode(404)
                        .body("error.code", equalTo("QUIZ_SESSION_NOT_FOUND"));
                missing.then().statusCode(404);
                assertEquals(
                        foreign.jsonPath().getMap("error"), missing.jsonPath().getMap("error"));
            }
        }
        String admin = token("Admin", owner);
        answer(admin, session, question, false).then().statusCode(200);
        assertCompleted(finish(admin, session), session, 1, 0);
    }

    @Test
    void validatesSecurityAndPathWithoutRequiringARequestBody() throws Exception {
        String owner = UUID.randomUUID().toString();
        long session = session(owner, 1);
        given().post(BASE + session + "/finish").then().statusCode(401);
        for (String role : List.of("Guest", "user", "admin")) {
            finish(token(role, owner), session).then().statusCode(403);
        }
        for (String subject : List.of(" ", "x".repeat(256))) {
            finish(token("User", subject), session).then().statusCode(401);
        }
        String token = token("User", owner);
        for (String id : List.of("0", "-1")) {
            given().auth().oauth2(token).post(BASE + id + "/finish").then().statusCode(400);
        }
        for (String id : List.of("invalid", "9223372036854775808")) {
            given().auth().oauth2(token).post(BASE + id + "/finish").then().statusCode(404);
        }
    }

    @Test
    void persistenceFailureRollsBackStatusAndCompletionTime() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 1);
        long question = snapshot(session, 0);
        answer(token, session, question, true).then().statusCode(200);
        List<String> before = history(session);
        sql(
                "CREATE TRIGGER quiz_finish_test_failure AFTER UPDATE ON quiz_sessions FOR EACH ROW"
                        + " BEGIN IF NEW.id="
                        + session
                        + " AND NEW.status='COMPLETED' THEN SIGNAL SQLSTATE '45000' "
                        + "SET MESSAGE_TEXT='forced finish failure'; END IF; END");
        try {
            finish(token, session).then().statusCode(500);
            assertEquals(
                    1,
                    number(
                            "SELECT COUNT(*) FROM quiz_sessions WHERE id="
                                    + session
                                    + " AND status='IN_PROGRESS' AND completed_at IS NULL AND"
                                    + " version=0"));
            assertEquals(before, history(session));
        } finally {
            sql("DROP TRIGGER quiz_finish_test_failure");
        }
        assertCompleted(finish(token, session), session, 1, 1);
    }

    @Test
    void concurrentFinishesCommitExactlyOneCompletion() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 1);
        long question = snapshot(session, 0);
        answer(token, session, question, true).then().statusCode(200);
        List<Response> results =
                race(session, () -> finish(token, session), () -> finish(token, session));
        assertEquals(
                List.of(200, 409), results.stream().map(Response::statusCode).sorted().toList());
        assertCompleted(
                results.stream()
                        .filter(response -> response.statusCode() == 200)
                        .findFirst()
                        .orElseThrow(),
                session,
                1,
                1);
        assertEquals(1, number("SELECT version FROM quiz_sessions WHERE id=" + session));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void finalAnswerAndFinishSerializeWithoutLosingScore(boolean finishFirst) throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 1);
        long question = snapshot(session, 0);
        long choice = option(question, true);
        Supplier<Response> answer =
                () ->
                        given().auth()
                                .oauth2(token)
                                .contentType("application/json")
                                .body(
                                        Map.of(
                                                "sessionQuestionId",
                                                question,
                                                "selectedOptionId",
                                                choice))
                                .post(BASE + session + "/answers");
        Supplier<Response> finish = () -> finish(token, session);
        List<Response> results =
                race(session, finishFirst ? finish : answer, finishFirst ? answer : finish);
        Response answerResult = results.get(finishFirst ? 1 : 0);
        Response finishResult = results.get(finishFirst ? 0 : 1);
        answerResult.then().statusCode(200).body("data.score", equalTo(1));
        // MySQL may schedule either waiter first. A premature finish must be retried explicitly.
        if (finishResult.statusCode() == 409) {
            finishResult.then().body("error.code", equalTo("QUIZ_FINISH_CONFLICT"));
            finishResult = finish(token, session);
        }
        assertCompleted(finishResult, session, 1, 1);
        assertCompletedReads(token, session, 1, 1);
        assertEquals(1, number("SELECT version FROM quiz_sessions WHERE id=" + session));
        assertEquals(
                1,
                number("SELECT COUNT(*) FROM quiz_answers WHERE session_question_id=" + question));
        answer(token, session, question, false).then().statusCode(409);
    }

    private List<Response> race(
            long session, Supplier<Response> firstRequest, Supplier<Response> secondRequest)
            throws Exception {
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
            first = CompletableFuture.supplyAsync(firstRequest);
            awaitWaiters(session, 1);
            second = CompletableFuture.supplyAsync(secondRequest);
            awaitWaiters(session, 2);
            assertFalse(first.isDone());
            assertFalse(second.isDone());
            transaction.commit().await().atMost(TIMEOUT);
            committed = true;
            return List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS));
        } finally {
            if (!committed) transaction.rollback().await().atMost(TIMEOUT);
            connection.close().await().atMost(TIMEOUT);
            if (first != null) first.get(15, TimeUnit.SECONDS);
            if (second != null) second.get(15, TimeUnit.SECONDS);
        }
    }

    private void awaitWaiters(long session, int expected) throws InterruptedException {
        long deadline = System.nanoTime() + TIMEOUT.toNanos();
        do {
            if (number(
                    "SELECT COUNT(DISTINCT w.REQUESTING_ENGINE_TRANSACTION_ID) FROM"
                            + " performance_schema.data_lock_waits w JOIN"
                            + " performance_schema.data_locks l ON l.ENGINE=w.ENGINE AND"
                            + " l.ENGINE_LOCK_ID=w.BLOCKING_ENGINE_LOCK_ID WHERE"
                            + " l.OBJECT_SCHEMA=DATABASE() AND l.OBJECT_NAME='quiz_sessions'"
                            + " AND l.LOCK_DATA='"
                            + session
                            + "'")
                    >= expected) return;
            Thread.sleep(50);
        } while (System.nanoTime() < deadline);
        fail("Requests must overlap at the session lock");
    }

    private void assertCompleted(Response response, long session, int total, int correct) {
        response.then()
                .statusCode(200)
                .body("data.sessionId", equalTo((int) session))
                .body("data.status", equalTo("COMPLETED"))
                .body("data.questionCount", equalTo(total))
                .body("data.correctCount", equalTo(correct))
                .body("data.incorrectCount", equalTo(total - correct))
                .body("data.score", equalTo(correct));
        assertNotNull(response.jsonPath().getString("data.completedAt"));
    }

    private void assertCompletedReads(String token, long session, int total, int score) {
        Response summary = given().auth().oauth2(token).get(BASE + session);
        summary.then()
                .statusCode(200)
                .body("data.status", equalTo("COMPLETED"))
                .body("data.answeredCount", equalTo(total))
                .body("data.score", equalTo(score));
        assertEquals(
                Set.of("sessionId", "status", "questionCount", "answeredCount", "score"),
                summary.jsonPath().getMap("data").keySet());
        Response next = given().auth().oauth2(token).get(BASE + session + "/next");
        next.then()
                .statusCode(200)
                .body("data.status", equalTo("COMPLETED"))
                .body("data.question", nullValue());
        assertEquals(
                Set.of("sessionId", "status", "question"), next.jsonPath().getMap("data").keySet());
    }

    private List<String> history(long session) {
        List<String> rows = new ArrayList<>();
        for (String query :
                List.of(
                        "SELECT * FROM quiz_session_questions WHERE session_id="
                                + session
                                + " ORDER BY id",
                        "SELECT o.* FROM quiz_session_options o JOIN quiz_session_questions q ON"
                                + " q.id=o.session_question_id WHERE q.session_id="
                                + session
                                + " ORDER BY o.id",
                        "SELECT a.* FROM quiz_answers a JOIN quiz_session_questions q ON"
                                + " q.id=a.session_question_id WHERE q.session_id="
                                + session
                                + " ORDER BY a.session_question_id")) {
            pool.query(query)
                    .execute()
                    .await()
                    .atMost(TIMEOUT)
                    .forEach(row -> rows.add(row.toJson().encode()));
        }
        return rows;
    }

    private Response finish(String token, long session) {
        return given().auth().oauth2(token).post(BASE + session + "/finish");
    }

    private Response answer(String token, long session, long question, boolean correct) {
        return given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(
                        Map.of(
                                "sessionQuestionId",
                                question,
                                "selectedOptionId",
                                option(question, correct)))
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
