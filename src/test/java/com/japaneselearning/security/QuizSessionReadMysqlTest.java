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
import java.util.Set;
import java.util.UUID;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizSessionReadMysqlTest {
    private static final String BASE = "/api/v1/kanji-quiz/sessions/";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    private static final String READING = "\u304c\u3063\u3053\u3046";

    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;

    @Test
    void repeatedReadsReturnSafePersistedOrderWithoutChangingState() throws Exception {
        String owner = "Owner-" + UUID.randomUUID();
        long session = session(owner, 3);
        snapshot(session, 2);
        long first = snapshot(session, 0);
        snapshot(session, 1);
        String token = token("User", owner);
        String state =
                text(
                        "SELECT CONCAT(version, ':', updated_at, ':', status) FROM quiz_sessions"
                                + " WHERE id="
                                + session);
        long answers = number("SELECT COUNT(*) FROM quiz_answers");
        long progress = number("SELECT COUNT(*) FROM quiz_user_progress");

        Map<String, Object> expected = read(token, session, "/next").jsonPath().getMap("data");
        for (int i = 0; i < 3; i++) {
            Response summary = read(token, session, "");
            summary.then()
                    .body("data.sessionId", equalTo((int) session))
                    .body("data.status", equalTo("IN_PROGRESS"))
                    .body("data.questionCount", equalTo(3))
                    .body("data.answeredCount", equalTo(0))
                    .body("data.score", equalTo(0));
            assertEquals(
                    Set.of("sessionId", "status", "questionCount", "answeredCount", "score"),
                    summary.jsonPath().getMap("data").keySet());
            Response next = read(token, session, "/next");
            assertEquals(expected, next.jsonPath().getMap("data"));
            next.then()
                    .body("data.question.sessionQuestionId", equalTo((int) first))
                    .body("data.question.questionNumber", equalTo(0))
                    .body("data.question.sentenceReading", equalTo(READING))
                    .body("data.question.targetStart", equalTo(0))
                    .body("data.question.targetLength", equalTo(4));
            assertEquals(
                    Set.of("sessionId", "status", "question"),
                    next.jsonPath().getMap("data").keySet());
            assertEquals(
                    Set.of(
                            "sessionQuestionId",
                            "questionNumber",
                            "sentenceReading",
                            "targetStart",
                            "targetLength",
                            "options"),
                    next.jsonPath().getMap("data.question").keySet());
            assertEquals(
                    List.of("choice-2", "choice-0", "choice-3", "choice-1"),
                    next.jsonPath().getList("data.question.options.text"));
            assertEquals(
                    numbers(
                            "SELECT id FROM quiz_session_options WHERE session_question_id="
                                    + first
                                    + " ORDER BY id"),
                    next.jsonPath().getList("data.question.options.id", Long.class));
            for (Map<String, Object> option :
                    next.jsonPath().<Map<String, Object>>getList("data.question.options")) {
                assertEquals(Set.of("id", "text"), option.keySet());
            }
        }
        assertEquals(
                state,
                text(
                        "SELECT CONCAT(version, ':', updated_at, ':', status) FROM quiz_sessions"
                                + " WHERE id="
                                + session));
        assertEquals(answers, number("SELECT COUNT(*) FROM quiz_answers"));
        assertEquals(progress, number("SELECT COUNT(*) FROM quiz_user_progress"));
    }

    @Test
    void skipsAnsweredHolesAndCountsOnlyThisSessionsSubmittedAnswers() throws Exception {
        String owner = "Answers-" + UUID.randomUUID();
        long session = session(owner, 3);
        long last = snapshot(session, 2);
        long first = snapshot(session, 0);
        long middle = snapshot(session, 1);
        long other = snapshot(session(owner, 1), 0);
        answer(other, true);
        answer(middle, false);
        String token = token("User", owner);
        read(token, session, "")
                .then()
                .body("data.answeredCount", equalTo(1))
                .body("data.score", equalTo(0));
        read(token, session, "/next")
                .then()
                .body("data.question.sessionQuestionId", equalTo((int) first));
        answer(first, true);
        read(token, session, "")
                .then()
                .body("data.answeredCount", equalTo(2))
                .body("data.score", equalTo(1));
        read(token, session, "/next")
                .then()
                .body("data.question.sessionQuestionId", equalTo((int) last));
        answer(last, true);
        read(token, session, "")
                .then()
                .body("data.answeredCount", equalTo(3))
                .body("data.score", equalTo(2))
                .body("data.status", equalTo("IN_PROGRESS"));
        assertNoNext(token, session, "IN_PROGRESS");
        assertEquals("IN_PROGRESS", text("SELECT status FROM quiz_sessions WHERE id=" + session));
    }

    @Test
    void emptyAndTerminalSessionsHaveNoNextWithoutTransitions() throws Exception {
        String owner = "Terminal-" + UUID.randomUUID();
        String token = token("User", owner);
        long empty = session(owner, 1);
        assertNoNext(token, empty, "IN_PROGRESS");
        read(token, empty, "")
                .then()
                .body("data.answeredCount", equalTo(0))
                .body("data.score", equalTo(0));
        for (String status : List.of("COMPLETED", "ABANDONED")) {
            long session = session(owner, 1);
            snapshot(session, 0);
            sql(
                    "UPDATE quiz_sessions SET status='"
                            + status
                            + "', completed_at=UTC_TIMESTAMP(6) WHERE id="
                            + session);
            read(token, session, "").then().body("data.status", equalTo(status));
            assertNoNext(token, session, status);
            assertEquals(status, text("SELECT status FROM quiz_sessions WHERE id=" + session));
        }
    }

    @Test
    void foreignAndMissingSessionsAreIndistinguishableEvenForAdmins() throws Exception {
        String owner = "Case-Owner-" + UUID.randomUUID();
        long session = session(owner, 1);
        snapshot(session, 0);
        for (String role : List.of("User", "Admin")) {
            read(token(role, owner), session, "").then().statusCode(200);
            read(token(role, owner), session, "/next").then().statusCode(200);
            for (String subject : List.of(owner.toLowerCase(), owner + " ", "stranger")) {
                for (String suffix : List.of("", "/next")) {
                    Response foreign =
                            given().auth()
                                    .oauth2(token(role, subject))
                                    .get(BASE + session + suffix);
                    Response missing =
                            given().auth()
                                    .oauth2(token(role, subject))
                                    .get(BASE + Long.MAX_VALUE + suffix);
                    foreign.then()
                            .statusCode(404)
                            .body("error.code", equalTo("QUIZ_SESSION_NOT_FOUND"));
                    missing.then().statusCode(404);
                    assertEquals(
                            foreign.jsonPath().getMap("error"), missing.jsonPath().getMap("error"));
                }
            }
        }
    }

    @Test
    void validatesIdsRolesAndSubjectsOnBothEndpoints() throws Exception {
        String owner = "Security-" + UUID.randomUUID();
        long session = session(owner, 1);
        for (String suffix : List.of("", "/next")) {
            given().get(BASE + session + suffix).then().statusCode(401);
            for (String role : List.of("Guest", "user", "admin")) {
                given().auth()
                        .oauth2(token(role, owner))
                        .get(BASE + session + suffix)
                        .then()
                        .statusCode(403);
            }
            for (String subject : List.of(" ", "x".repeat(256))) {
                given().auth()
                        .oauth2(token("User", subject))
                        .get(BASE + session + suffix)
                        .then()
                        .statusCode(401);
            }
            for (String id : List.of("0", "-1")) {
                given().auth()
                        .oauth2(token("User", owner))
                        .get(BASE + id + suffix)
                        .then()
                        .statusCode(400);
            }
            for (String id : List.of("invalid", "9223372036854775808")) {
                given().auth()
                        .oauth2(token("User", owner))
                        .get(BASE + id + suffix)
                        .then()
                        .statusCode(404);
            }
        }
    }

    @Test
    void createdSessionRetainsItsSnapshotAfterBankAndSourceChanges() throws Exception {
        String owner = "Snapshot-" + UUID.randomUUID();
        String admin = token("Admin", owner);
        String marker = UUID.randomUUID().toString();
        sql(
                "INSERT INTO"
                        + " example_sentences(japanese_text,japanese_reading,meaning_vi,meaning_en)"
                        + " VALUES ('"
                        + marker
                        + "','"
                        + READING
                        + "','vi','en')");
        long example =
                number("SELECT id FROM example_sentences WHERE japanese_text='" + marker + "'");
        String bank = "/api/v1/admin/kanji-quiz/questions";
        long question =
                given().auth()
                        .oauth2(admin)
                        .contentType("application/json")
                        .body(
                                Map.of(
                                        "content",
                                        Map.of(
                                                "sourceType",
                                                "EXAMPLE",
                                                "exampleId",
                                                example,
                                                "targetStart",
                                                0,
                                                "targetLength",
                                                4,
                                                "targetReading",
                                                READING,
                                                "explanationVi",
                                                "original vi",
                                                "explanationEn",
                                                "original en",
                                                "levelIds",
                                                List.of(),
                                                "lessonIds",
                                                List.of()),
                                        "options",
                                        List.of(
                                                Map.of("text", "correct", "correct", true),
                                                Map.of("text", "wrong-1", "correct", false),
                                                Map.of("text", "wrong-2", "correct", false),
                                                Map.of("text", "wrong-3", "correct", false))))
                        .post(bank)
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data.id");
        long version =
                given().auth()
                        .oauth2(admin)
                        .get(bank + "/" + question)
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data.version");
        given().auth()
                .oauth2(admin)
                .contentType("application/json")
                .body(Map.of("version", version))
                .post(bank + "/" + question + "/publish")
                .then()
                .statusCode(200);
        // Isolate this question with its own lesson instead of relying on random bank selection.
        String levelCode = UUID.randomUUID().toString().substring(0, 8);
        long displayOrder = number("SELECT MAX(display_order) FROM jlpt_levels") + 1;
        sql(
                "INSERT INTO jlpt_levels(code,name,display_order) VALUES ('"
                        + levelCode
                        + "','Read test',"
                        + displayOrder
                        + ")");
        long level = number("SELECT id FROM jlpt_levels WHERE code='" + levelCode + "'");
        sql(
                "INSERT INTO lessons(level_id,lesson_number,title,display_order) VALUES ("
                        + level
                        + ",1,'Read test',1)");
        long lesson = number("SELECT id FROM lessons WHERE level_id=" + level);
        sql(
                "INSERT INTO quiz_question_lessons(question_id,lesson_id) VALUES ("
                        + question
                        + ","
                        + lesson
                        + ")");
        long session =
                given().auth()
                        .oauth2(admin)
                        .contentType("application/json")
                        .body(Map.of("lessonId", lesson, "questionCount", 1))
                        .post("/api/v1/kanji-quiz/sessions")
                        .then()
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data.sessionId");
        Map<String, Object> original =
                read(admin, session, "/next").jsonPath().getMap("data.question");
        assertEquals(READING, original.get("sentenceReading"));
        assertEquals(4, ((List<?>) original.get("options")).size());
        Response bankQuestion = given().auth().oauth2(admin).get(bank + "/" + question);
        bankQuestion.then().statusCode(200);
        long changedOption =
                bankQuestion.jsonPath().getLong("data.options.find { !it.correct }.id");
        given().auth()
                .oauth2(admin)
                .contentType("application/json")
                .body(
                        Map.of(
                                "version",
                                bankQuestion.jsonPath().getLong("data.version"),
                                "optionId",
                                changedOption))
                .put(bank + "/" + question + "/correct-option")
                .then()
                .statusCode(200);
        sql(
                "UPDATE quiz_questions SET explanation_vi='edited vi', explanation_en='edited en'"
                        + " WHERE id="
                        + question);

        sql(
                "UPDATE example_sentences SET japanese_reading=CONCAT(japanese_reading,'.') WHERE"
                        + " id="
                        + example);
        sql(
                "UPDATE quiz_question_options SET option_text=CONCAT(option_text,'-edited') WHERE"
                        + " question_id="
                        + question);
        assertEquals(original, read(admin, session, "/next").jsonPath().getMap("data.question"));
        sql("DELETE FROM quiz_questions WHERE id=" + question);
        sql("DELETE FROM example_sentences WHERE id=" + example);
        assertEquals(original, read(admin, session, "/next").jsonPath().getMap("data.question"));
        read(admin, session, "")
                .then()
                .body("data.questionCount", equalTo(1))
                .body("data.answeredCount", equalTo(0))
                .body("data.score", equalTo(0));
        long snapshotId = ((Number) original.get("sessionQuestionId")).longValue();
        long correctOption =
                number(
                        "SELECT id FROM quiz_session_options WHERE session_question_id="
                                + snapshotId
                                + " AND is_correct=1");
        given().auth()
                .oauth2(admin)
                .contentType("application/json")
                .body(Map.of("sessionQuestionId", snapshotId, "selectedOptionId", correctOption))
                .post(BASE + session + "/answers")
                .then()
                .statusCode(200)
                .body("data.correct", equalTo(true))
                .body("data.explanationVi", equalTo("original vi"))
                .body("data.explanationEn", equalTo("original en"))
                .body("data.correctOptionId", equalTo((int) correctOption))
                .body("data.score", equalTo(1));
    }

    private void assertNoNext(String token, long session, String status) {
        Response response = read(token, session, "/next");
        response.then().body("data.status", equalTo(status));
        assertTrue(response.jsonPath().getMap("data").containsKey("question"));
        assertNull(response.jsonPath().get("data.question"));
    }

    private Response read(String token, long session, String suffix) {
        Response response = given().auth().oauth2(token).get(BASE + session + suffix);
        response.then().statusCode(200);
        return response;
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

    private long snapshot(long session, int number) {
        // Insert out of question order with a non-text-sorted option order.
        // NULL bank IDs represent immutable history after the bank question is deleted.
        sql(
                "INSERT INTO"
                        + " quiz_session_questions(session_id,question_version,question_number,source_type,sentence_reading,target_start,target_length,target_reading,explanation_vi)"
                        + " VALUES ("
                        + session
                        + ",1,"
                        + number
                        + ",'CUSTOM','"
                        + READING
                        + "',0,4,'"
                        + READING
                        + "','private explanation')");
        long id =
                number(
                        "SELECT id FROM quiz_session_questions WHERE session_id="
                                + session
                                + " AND question_number="
                                + number);
        for (int choice : List.of(2, 0, 3, 1)) {
            sql(
                    "INSERT INTO quiz_session_options(session_question_id,option_text,is_correct)"
                            + " VALUES ("
                            + id
                            + ",'choice-"
                            + choice
                            + "',"
                            + (choice == 0 ? 1 : 0)
                            + ")");
        }
        return id;
    }

    private void answer(long question, boolean correct) {
        sql(
                "INSERT INTO quiz_answers(session_question_id,selected_option_id) SELECT"
                        + " session_question_id,id FROM quiz_session_options WHERE session_question_id="
                        + question
                        + " AND is_correct="
                        + (correct ? 1 : 0)
                        + " ORDER BY id LIMIT 1");
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

    private String text(String query) {
        return pool.query(query).execute().await().atMost(TIMEOUT).iterator().next().getString(0);
    }

    private List<Long> numbers(String query) {
        var result = new java.util.ArrayList<Long>();
        pool.query(query)
                .execute()
                .await()
                .atMost(TIMEOUT)
                .forEach(row -> result.add(row.getLong(0)));
        return result;
    }
}
