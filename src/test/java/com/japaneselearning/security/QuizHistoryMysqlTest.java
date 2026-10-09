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

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizHistoryMysqlTest {
    private static final String BASE = "/api/v1/kanji-quiz/history";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;

    @Test
    void listsOnlyOwnedCompletedSessionsWithStablePaginationAndTotals() throws Exception {
        String owner = "Owner-" + UUID.randomUUID();
        String token = token("User", owner);
        long oldest = completed(owner, "2026-01-01 00:00:00", true);
        long tieFirst = completed(owner, "2026-02-01 00:00:00", false);
        long tieLast = completed(owner, "2026-02-01 00:00:00", true);
        long laterIdEarlierTime = completed(owner, "2026-01-15 00:00:00", false);
        completed(owner.toLowerCase(), "2026-03-01 00:00:00", true);
        completed(owner + " ", "2026-03-01 00:00:00", true);
        long incomplete = session(owner, 1);
        snapshot(incomplete, 0);
        long exhausted = session(owner, 1);
        answer(snapshot(exhausted, 0), true);
        long abandoned = session(owner, 1);
        snapshot(abandoned, 0);
        sql(
                "UPDATE quiz_sessions SET status='ABANDONED', completed_at=UTC_TIMESTAMP(6) WHERE"
                        + " id="
                        + abandoned);
        for (String role : List.of("User", "Admin")) {
            Response first = page(token(role, owner), 0, 2);
            first.then()
                    .statusCode(200)
                    .body("data.page", equalTo(0))
                    .body("data.size", equalTo(2))
                    .body("data.totalElements", equalTo(4))
                    .body("data.totalPages", equalTo(2));
            assertEquals(
                    List.of(tieLast, tieFirst),
                    first.jsonPath().getList("data.items.sessionId", Long.class));
            assertEquals(
                    List.of(1, 0), first.jsonPath().getList("data.items.score", Integer.class));
            assertEquals(
                    List.of(0, 1),
                    first.jsonPath().getList("data.items.incorrectCount", Integer.class));
            Response second = page(token(role, owner), 1, 2);
            assertEquals(
                    List.of(laterIdEarlierTime, oldest),
                    second.jsonPath().getList("data.items.sessionId", Long.class));
            assertEquals(4, second.jsonPath().getInt("data.totalElements"));
            page(token(role, owner), 2, 2)
                    .then()
                    .statusCode(200)
                    .body("data.items.size()", equalTo(0))
                    .body("data.totalElements", equalTo(4));
        }
        Response defaults = given().auth().oauth2(token).get(BASE);
        defaults.then()
                .statusCode(200)
                .body("data.page", equalTo(0))
                .body("data.size", equalTo(20))
                .body("data.items.size()", equalTo(4));
        assertEquals(
                Set.of("items", "page", "size", "totalElements", "totalPages"),
                defaults.jsonPath().getMap("data").keySet());
        assertEquals(
                Set.of(
                        "sessionId",
                        "status",
                        "levelId",
                        "lessonId",
                        "completedAt",
                        "questionCount",
                        "correctCount",
                        "incorrectCount",
                        "score"),
                defaults.jsonPath().getMap("data.items[0]").keySet());
        defaults.then()
                .body("data.items[0].levelId", nullValue())
                .body("data.items[0].lessonId", nullValue());
        page(token, 0, 100).then().statusCode(200).body("data.items.size()", equalTo(4));
        page(token, Integer.MAX_VALUE, 1)
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(0));
    }

    @Test
    void returnsEmptyPageForNewUserAndRejectsInvalidPagination() throws Exception {
        String token = token("User", UUID.randomUUID().toString());
        page(token, 0, 20)
                .then()
                .statusCode(200)
                .body("data.items.size()", equalTo(0))
                .body("data.totalElements", equalTo(0))
                .body("data.totalPages", equalTo(0));
        page(token, 4, 20).then().statusCode(200).body("data.items.size()", equalTo(0));
        for (int[] pair :
                List.of(
                        new int[]{-1, 20},
                        new int[]{0, 0},
                        new int[]{0, -1},
                        new int[]{0, 101},
                        new int[]{Integer.MAX_VALUE, 100})) {
            page(token, pair[0], pair[1])
                    .then()
                    .statusCode(400)
                    .body("error.code", equalTo("QUIZ_HISTORY_INVALID"));
        }
        for (String query : List.of("page=abc", "page=2147483648", "size=abc", "size=2147483648")) {
            given().auth().oauth2(token).get(BASE + "?" + query).then().statusCode(404);
        }
    }

    @Test
    void detailUsesPersistedQuestionAndOptionOrderAndDoesNotMutateAnything() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 2);
        long last = snapshot(session, 7);
        long first = snapshot(session, 2);
        answer(first, true);
        answer(last, false);
        sql(
                "UPDATE quiz_sessions SET status='COMPLETED', completed_at=UTC_TIMESTAMP(6) WHERE"
                        + " id="
                        + session);
        sql("UPDATE quiz_session_questions SET explanation_en=NULL WHERE id=" + last);
        List<String> before = state(session);
        long progress = number("SELECT COUNT(*) FROM quiz_user_progress");
        Response detail = detail(token, session);
        detail.then()
                .statusCode(200)
                .body("data.session.questionCount", equalTo(2))
                .body("data.session.correctCount", equalTo(1))
                .body("data.session.incorrectCount", equalTo(1))
                .body("data.session.score", equalTo(1));
        assertEquals(
                List.of(first, last),
                detail.jsonPath().getList("data.questions.sessionQuestionId", Long.class));
        assertEquals(
                List.of(2, 7),
                detail.jsonPath().getList("data.questions.questionNumber", Integer.class));
        for (int i = 0; i < 2; i++) {
            long question = i == 0 ? first : last;
            String path = "data.questions[" + i + "]";
            detail.then()
                    .body(path + ".sentenceReading", equalTo("reading"))
                    .body(path + ".targetStart", equalTo(0))
                    .body(path + ".targetLength", equalTo(7))
                    .body(path + ".correct", equalTo(i == 0))
                    .body(path + ".explanationVi", equalTo("snapshot vi"));
            assertEquals(
                    option(question, i == 0),
                    detail.jsonPath().getLong(path + ".selectedOptionId"));
            assertEquals(
                    option(question, true), detail.jsonPath().getLong(path + ".correctOptionId"));
            assertEquals(
                    List.of("choice-2", "choice-0", "choice-3", "choice-1"),
                    detail.jsonPath().getList(path + ".options.text"));
            assertNotNull(detail.jsonPath().getString(path + ".answeredAt"));
        }
        assertTrue(detail.jsonPath().getMap("data.questions[1]").containsKey("explanationEn"));
        detail.then().body("data.questions[1].explanationEn", nullValue());
        for (int i = 0; i < 3; i++) {
            assertEquals(
                    detail.jsonPath().getMap("data"),
                    detail(token, session).jsonPath().getMap("data"));
            Response list = page(token, 0, 20);
            assertEquals(
                    detail.jsonPath().getMap("data.session"),
                    list.jsonPath().getMap("data.items[0]"));
        }
        assertEquals(before, state(session));
        assertEquals(progress, number("SELECT COUNT(*) FROM quiz_user_progress"));
    }

    @Test
    void missingForeignAndIncompleteDetailsShareTheSame404EvenForAdmin() throws Exception {
        String owner = "Owner-" + UUID.randomUUID();
        long completed = completed(owner, "2026-01-01 00:00:00", true);
        long incomplete = session(owner, 1);
        long exhausted = session(owner, 1);
        answer(snapshot(exhausted, 0), true);
        long abandoned = session(owner, 1);
        sql(
                "UPDATE quiz_sessions SET status='ABANDONED', completed_at=UTC_TIMESTAMP(6) WHERE"
                        + " id="
                        + abandoned);
        for (String role : List.of("User", "Admin")) {
            String ownerToken = token(role, owner);
            detail(ownerToken, completed).then().statusCode(200);
            Response missing = detail(ownerToken, Long.MAX_VALUE);
            missing.then().statusCode(404).body("error.code", equalTo("QUIZ_SESSION_NOT_FOUND"));
            for (long id : List.of(incomplete, exhausted, abandoned)) {
                Response response = detail(ownerToken, id);
                response.then().statusCode(404);
                assertEquals(
                        missing.jsonPath().getMap("error"), response.jsonPath().getMap("error"));
                assertFalse(
                        response.jsonPath().getMap("error").toString().contains("correctOptionId"));
            }
            for (String subject : List.of(owner.toLowerCase(), owner + " ", "stranger")) {
                Response foreign = detail(token(role, subject), completed);
                foreign.then().statusCode(404);
                assertEquals(
                        missing.jsonPath().getMap("error"), foreign.jsonPath().getMap("error"));
                page(token(role, subject), 0, 20)
                        .then()
                        .statusCode(200)
                        .body("data.items.size()", equalTo(0));
            }
        }
    }

    @Test
    void historySurvivesBankAndExampleEditsAndDeletionForBothSources() throws Exception {
        String owner = UUID.randomUUID().toString();
        String token = token("User", owner);
        long session = session(owner, 2);
        String marker = UUID.randomUUID().toString();
        sql(
                "INSERT INTO"
                        + " example_sentences(japanese_text,japanese_reading,meaning_vi,meaning_en)"
                        + " VALUES ('"
                        + marker
                        + "','reading','source','source')");
        long example =
                number("SELECT id FROM example_sentences WHERE japanese_text='" + marker + "'");
        List<Long> bankIds = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            String type = i == 0 ? "EXAMPLE" : "CUSTOM";
            sql(
                    "INSERT INTO"
                            + " quiz_questions(source_type,example_sentence_id,sentence_reading,target_start,target_length,target_reading)"
                            + " VALUES ('"
                            + type
                            + "',"
                            + (i == 0 ? example : "NULL")
                            + ","
                            + (i == 0 ? "NULL" : "'reading'")
                            + ",0,7,'reading')");
            long bank = number("SELECT MAX(id) FROM quiz_questions");
            bankIds.add(bank);
            sql(
                    "INSERT INTO quiz_question_options(question_id,option_text,is_correct) VALUES ("
                            + bank
                            + ",'bank choice',1)");
            long snapshot = snapshot(session, i);
            sql(
                    "UPDATE quiz_session_questions SET question_id="
                            + bank
                            + ",source_type='"
                            + type
                            + "',source_example_id="
                            + (i == 0 ? example : "NULL")
                            + " WHERE id="
                            + snapshot);
            answer(snapshot, i == 0);
        }
        given().auth()
                .oauth2(token)
                .post("/api/v1/kanji-quiz/sessions/" + session + "/finish")
                .then()
                .statusCode(200);
        Map<String, Object> original =
                detail(token, session).then().statusCode(200).extract().jsonPath().getMap("data");
        sql(
                "UPDATE example_sentences SET japanese_reading='changed"
                        + " source',meaning_vi='changed' WHERE id="
                        + example);
        for (long bank : bankIds) {
            sql(
                    "UPDATE quiz_questions SET explanation_vi='changed',explanation_en='changed'"
                            + " WHERE id="
                            + bank);
            sql(
                    "UPDATE quiz_question_options SET option_text='changed',is_correct=0 WHERE"
                            + " question_id="
                            + bank);
        }
        assertEquals(original, detail(token, session).jsonPath().getMap("data"));
        for (long bank : bankIds) sql("DELETE FROM quiz_questions WHERE id=" + bank);
        sql("DELETE FROM example_sentences WHERE id=" + example);
        assertEquals(original, detail(token, session).jsonPath().getMap("data"));
        assertEquals(
                original.get("session"), page(token, 0, 20).jsonPath().getMap("data.items[0]"));
    }

    @Test
    void bothEndpointsEnforceAuthenticationRolesAndUsableSubjects() throws Exception {
        for (String path : List.of(BASE, BASE + "/1")) {
            given().get(path).then().statusCode(401);
            for (String role : List.of("Guest", "user", "admin")) {
                given().auth().oauth2(token(role, "owner")).get(path).then().statusCode(403);
            }
            for (String subject : List.of(" ", "x".repeat(256))) {
                given().auth().oauth2(token("User", subject)).get(path).then().statusCode(401);
            }
        }
        String token = token("User", "owner");
        for (String id : List.of("0", "-1")) {
            given().auth().oauth2(token).get(BASE + "/" + id).then().statusCode(400);
        }
        for (String id : List.of("invalid", "9223372036854775808")) {
            given().auth().oauth2(token).get(BASE + "/" + id).then().statusCode(404);
        }
    }

    private Response page(String token, int page, int size) {
        return given().auth()
                .oauth2(token)
                .queryParam("page", page)
                .queryParam("size", size)
                .get(BASE);
    }

    private Response detail(String token, long id) {
        return given().auth().oauth2(token).get(BASE + "/" + id);
    }

    private long completed(String owner, String time, boolean correct) {
        long session = session(owner, 1);
        answer(snapshot(session, 0), correct);
        sql(
                "UPDATE quiz_sessions SET status='COMPLETED',completed_at='"
                        + time
                        + "' WHERE id="
                        + session);
        return session;
    }

    private void answer(long question, boolean correct) {
        sql(
                "INSERT INTO quiz_answers(session_question_id,selected_option_id) VALUES ("
                        + question
                        + ","
                        + option(question, correct)
                        + ")");
    }

    private List<String> state(long session) {
        List<String> rows = new ArrayList<>();
        for (String query :
                List.of(
                        "SELECT * FROM quiz_sessions WHERE id=" + session,
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
        for (int i : List.of(2, 0, 3, 1)) {
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
