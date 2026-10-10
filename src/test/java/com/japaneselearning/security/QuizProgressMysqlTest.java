package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;

import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse;
import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse.Bucket;
import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse.Classification;
import com.japaneselearning.quiz.player.dto.QuizProgressResponse;
import com.japaneselearning.quiz.service.QuizSessionSnapshotService;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import io.restassured.response.Response;
import io.vertx.mutiny.mysqlclient.MySQLPool;

import jakarta.inject.Inject;

import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizProgressMysqlTest {
    private static final String BASE = "/api/v1/kanji-quiz/progress";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);
    @Inject
    MySQLPool pool;
    @Inject
    QuizSessionSnapshotService creation;
    RsaJsonWebKey signingKey;

    @Test
    void overallUsesWeightedAccuracyAndPerSessionScoresOnlyForOwnedCompletions() throws Exception {
        String owner = "Owner-" + UUID.randomUUID();
        long first = session(owner, 5);
        for (int i = 0; i < 5; i++) answer(snapshot(first, i), i < 3);
        complete(first, "2026-01-01 00:00:00");
        completed(owner, "2026-02-01 00:00:00.123456", true);
        long incomplete = session(owner, 1);
        answer(snapshot(incomplete, 0), true);
        long abandoned = session(owner, 1);
        answer(snapshot(abandoned, 0), true);
        sql(
                "UPDATE quiz_sessions SET status='ABANDONED',completed_at='2026-03-01' WHERE id="
                        + abandoned);
        for (String other : List.of(owner.toLowerCase(), owner + " ", "other" + owner)) {
            completed(other, "2026-04-01 00:00:00", true);
        }
        for (String role : List.of("User", "Admin")) {
            QuizProgressResponse result = overall(token(role, owner));
            assertEquals(2, result.completedSessions());
            assertEquals(6, result.answeredCount());
            assertEquals(4, result.correctCount());
            assertEquals(2, result.incorrectCount());
            decimal("66.67", result.accuracyPercentage());
            assertEquals(3, result.bestScore());
            decimal("2", result.averageScore());
            assertEquals(
                    LocalDateTime.parse("2026-02-01T00:00:00.123456"), result.latestCompletedAt());
        }
        QuizProgressBreakdownResponse breakdown = breakdown(token("User", owner));
        assertEquals(1, breakdown.levels().size());
        bucket(breakdown.levels().get(0), Classification.UNKNOWN, null, null, 2, 6, 4, "66.67");
        assertEquals(breakdown.levels(), breakdown.lessons());
        given().auth()
                .oauth2(token("User", owner))
                .queryParam("userId", owner.toLowerCase())
                .get(BASE)
                .then()
                .statusCode(200)
                .body("data.completedSessions", equalTo(2));
    }

    @Test
    void emptyAndIncompleteHistoriesHaveZeroStatisticsAndNoBuckets() throws Exception {
        String owner = UUID.randomUUID().toString();
        String jwt = token("User", owner);
        for (int pass = 0; pass < 2; pass++) {
            QuizProgressResponse result = overall(jwt);
            assertEquals(0, result.completedSessions());
            assertEquals(0, result.answeredCount());
            assertEquals(0, result.correctCount());
            assertEquals(0, result.incorrectCount());
            assertEquals(0, result.bestScore());
            decimal("0", result.accuracyPercentage());
            decimal("0", result.averageScore());
            assertNull(result.latestCompletedAt());
            assertTrue(breakdown(jwt).levels().isEmpty());
            assertTrue(breakdown(jwt).lessons().isEmpty());
            long pending = session(owner, 1);
            answer(snapshot(pending, 0), true);
        }
    }

    @Test
    void classifiesMultipleAssignmentsWithoutCrossProductsAndKeepsUnknownSeparate()
            throws Throwable {
        String owner = UUID.randomUUID().toString();
        String jwt = token("User", owner);
        long levelA = level();
        long levelB = level();
        long lessonA = lesson(levelA);
        long lessonA2 = lesson(levelA);
        long lessonB = lesson(levelB);
        long custom = question(List.of(levelA, levelB), List.of(lessonA, lessonA2, lessonB), false);
        long example = question(List.of(), List.of(lessonA), true);
        long levelOnly = question(List.of(levelB), List.of(), false);
        long unassigned = question(List.of(), List.of(), false);
        long id =
                VertxContextSupport.subscribeAndAwait(
                        () ->
                                creation.create(
                                                owner,
                                                List.of(custom, example, levelOnly, unassigned))
                                        .map(s -> s.id));
        assertEquals(
                4,
                number(
                        "SELECT COUNT(*) FROM quiz_session_questions WHERE"
                                + " classifications_captured=TRUE AND session_id="
                                + id));
        List<Long> snapshots =
                numbers(
                        "SELECT id FROM quiz_session_questions WHERE session_id="
                                + id
                                + " ORDER BY question_number");
        for (int i = 0; i < snapshots.size(); i++) answer(snapshots.get(i), i != 1);
        given().auth()
                .oauth2(jwt)
                .post("/api/v1/kanji-quiz/sessions/" + id + "/finish")
                .then()
                .statusCode(200)
                .body("data.score", equalTo(3));
        completed(owner, "2026-01-01 00:00:00", false);
        QuizProgressBreakdownResponse result = breakdown(jwt);
        assertEquals(4, result.levels().size());
        bucket(result.levels().get(0), Classification.ASSIGNED, levelA, null, 1, 2, 1, "50");
        bucket(result.levels().get(1), Classification.ASSIGNED, levelB, null, 1, 2, 2, "100");
        bucket(result.levels().get(2), Classification.UNASSIGNED, null, null, 1, 1, 1, "100");
        bucket(result.levels().get(3), Classification.UNKNOWN, null, null, 1, 1, 0, "0");
        assertEquals(5, result.lessons().size());
        bucket(result.lessons().get(0), Classification.ASSIGNED, levelA, lessonA, 1, 2, 1, "50");
        bucket(result.lessons().get(1), Classification.ASSIGNED, levelA, lessonA2, 1, 1, 1, "100");
        bucket(result.lessons().get(2), Classification.ASSIGNED, levelB, lessonB, 1, 1, 1, "100");
        bucket(result.lessons().get(3), Classification.UNASSIGNED, null, null, 1, 2, 2, "100");
        bucket(result.lessons().get(4), Classification.UNKNOWN, null, null, 1, 1, 0, "0");
        QuizProgressResponse totals = overall(jwt);
        assertEquals(2, totals.completedSessions());
        assertEquals(5, totals.answeredCount());
        decimal("60", totals.accuracyPercentage());
        decimal("1.5", totals.averageScore());
        assertEquals(result, breakdown(jwt));
        assertTrue(breakdown(token("Admin", owner + "foreign")).levels().isEmpty());

        List<String> before = state(id);
        long progressRows = number("SELECT COUNT(*) FROM quiz_user_progress");
        overall(jwt);
        breakdown(jwt);
        assertEquals(before, state(id));
        assertEquals(progressRows, number("SELECT COUNT(*) FROM quiz_user_progress"));
        // Changes and deletion in every mutable classification source leave the snapshot report
        // intact.
        sql(
                "UPDATE lessons SET level_id="
                        + levelB
                        + ",lesson_number=99,display_order=99 WHERE id="
                        + lessonA);
        for (long bank : List.of(custom, example, levelOnly, unassigned)) {
            sql("DELETE FROM quiz_questions WHERE id=" + bank);
        }
        sql("DELETE FROM lessons WHERE id IN (" + lessonA + "," + lessonA2 + "," + lessonB + ")");
        sql("DELETE FROM jlpt_levels WHERE id IN (" + levelA + "," + levelB + ")");
        assertEquals(result, breakdown(jwt));
        assertEquals(totals, overall(jwt));
        given().auth()
                .oauth2(jwt)
                .get("/api/v1/kanji-quiz/history/" + id)
                .then()
                .statusCode(200)
                .body("data.session.score", equalTo(3))
                .body("data.session.levelId", nullValue())
                .body("data.session.lessonId", nullValue());
    }

    @Test
    void legacyQuestionsStayUnknownEvenWhenTheirBankHasCurrentAssignments() throws Exception {
        String owner = UUID.randomUUID().toString();
        long level = level();
        long bank = question(List.of(level), List.of(), false);
        long id = session(owner, 1);
        long snapshot = snapshot(id, 0);
        sql("UPDATE quiz_session_questions SET question_id=" + bank + " WHERE id=" + snapshot);
        answer(snapshot, true);
        complete(id, "2026-01-01 00:00:00");
        QuizProgressBreakdownResponse result = breakdown(token("User", owner));
        bucket(result.levels().get(0), Classification.UNKNOWN, null, null, 1, 1, 1, "100");
        assertEquals(1, result.levels().size());
        assertEquals(result.levels(), result.lessons());
        assertEquals(
                0,
                number(
                        "SELECT classifications_captured FROM quiz_session_questions WHERE id="
                                + snapshot));
    }

    @Test
    void endpointsEnforceJwtRolesSubjectsAndOwnership() throws Exception {
        for (String path : List.of(BASE, BASE + "/breakdown")) {
            given().get(path).then().statusCode(401);
            for (String role : List.of("Guest", "user", "admin")) {
                given().auth().oauth2(token(role, "owner")).get(path).then().statusCode(403);
            }
            for (String subject : List.of(" ", "x".repeat(256))) {
                given().auth().oauth2(token("User", subject)).get(path).then().statusCode(401);
            }
        }
    }

    @Test
    void roundsAverageScoreAndAccuracyWithoutIntegerDivision() throws Exception {
        String owner = UUID.randomUUID().toString();
        completed(owner, "2026-01-01 00:00:00", true);
        completed(owner, "2026-01-02 00:00:00", true);
        completed(owner, "2026-01-03 00:00:00", false);
        QuizProgressResponse result = overall(token("User", owner));
        decimal("0.67", result.averageScore());
        decimal("66.67", result.accuracyPercentage());
        assertEquals(1, result.bestScore());
    }

    private QuizProgressResponse overall(String jwt) {
        return given().auth()
                .oauth2(jwt)
                .get(BASE)
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getObject("data", QuizProgressResponse.class);
    }

    private QuizProgressBreakdownResponse breakdown(String jwt) {
        return given().auth()
                .oauth2(jwt)
                .get(BASE + "/breakdown")
                .then()
                .statusCode(200)
                .extract()
                .jsonPath()
                .getObject("data", QuizProgressBreakdownResponse.class);
    }

    private void decimal(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual));
    }

    private void bucket(
            Bucket actual,
            Classification classification,
            Long level,
            Long lesson,
            long sessions,
            long answered,
            long correct,
            String accuracy) {
        assertEquals(classification, actual.classification());
        assertEquals(level, actual.levelId());
        assertEquals(lesson, actual.lessonId());
        assertEquals(sessions, actual.completedSessions());
        assertEquals(answered, actual.answeredCount());
        assertEquals(correct, actual.correctCount());
        assertEquals(answered - correct, actual.incorrectCount());
        decimal(accuracy, actual.accuracyPercentage());
    }

    private void complete(long id, String time) {
        sql(
                "UPDATE quiz_sessions SET status='COMPLETED',completed_at='"
                        + time
                        + "' WHERE id="
                        + id);
    }

    private long question(List<Long> levels, List<Long> lessons, boolean example) throws Exception {
        String reading = "\u304c\u3063\u3053\u3046";
        String admin = token("Admin", "progress-admin");
        Map<String, Object> content = new HashMap<>();
        content.put("sourceType", example ? "EXAMPLE" : "CUSTOM");
        if (example) {
            String marker = UUID.randomUUID().toString();
            sql(
                    "INSERT INTO"
                            + " example_sentences(japanese_text,japanese_reading,meaning_vi,meaning_en)"
                            + " VALUES ('"
                            + marker
                            + "','"
                            + reading
                            + "','vi','en')");
            content.put(
                    "exampleId",
                    number(
                            "SELECT id FROM example_sentences WHERE japanese_text='"
                                    + marker
                                    + "'"));
        } else {
            content.put("sentenceReading", reading + UUID.randomUUID());
        }
        content.put("targetStart", 0);
        content.put("targetLength", 4);
        content.put("targetReading", reading);
        content.put("levelIds", levels);
        content.put("lessonIds", lessons);
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) options.add(Map.of("text", "choice-" + i, "correct", i == 0));
        Response created =
                given().auth()
                        .oauth2(admin)
                        .contentType("application/json")
                        .body(Map.of("content", content, "options", options))
                        .post("/api/v1/admin/kanji-quiz/questions");
        created.then().statusCode(200);
        long id = created.jsonPath().getLong("data.id");
        given().auth()
                .oauth2(admin)
                .contentType("application/json")
                .body(Map.of("version", created.jsonPath().getLong("data.version")))
                .post("/api/v1/admin/kanji-quiz/questions/" + id + "/publish")
                .then()
                .statusCode(200);
        return id;
    }

    private long level() {
        String code = UUID.randomUUID().toString().substring(0, 8);
        long order = number("SELECT MAX(display_order) FROM jlpt_levels") + 1;
        sql(
                "INSERT INTO jlpt_levels(code,name,display_order) VALUES ('"
                        + code
                        + "','Progress test',"
                        + order
                        + ")");
        return number("SELECT id FROM jlpt_levels WHERE code='" + code + "'");
    }

    private long lesson(long level) {
        long order = number("SELECT COUNT(*) FROM lessons WHERE level_id=" + level) + 1;
        sql(
                "INSERT INTO lessons(level_id,lesson_number,title,display_order) VALUES ("
                        + level
                        + ","
                        + order
                        + ",'Progress test',"
                        + order
                        + ")");
        return number("SELECT MAX(id) FROM lessons WHERE level_id=" + level);
    }

    private List<Long> numbers(String query) {
        List<Long> result = new ArrayList<>();
        pool.query(query)
                .execute()
                .await()
                .atMost(TIMEOUT)
                .forEach(row -> result.add(row.getLong(0)));
        return result;
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
