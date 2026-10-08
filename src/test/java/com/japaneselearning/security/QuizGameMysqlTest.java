package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizGameMysqlTest {
    private static final String BASE = "/api/v1/kanji-quiz";
    private static final String ADMIN = "/api/v1/admin/kanji-quiz/questions";
    private static final String READING = "\u304c\u3063\u3053\u3046";
    private static final Duration TIMEOUT = Duration.ofSeconds(10);

    @Inject
    MySQLPool pool;

    RsaJsonWebKey signingKey;
    private String admin;
    private String user;

    @BeforeEach
    void authenticate() throws Exception {
        admin = token("Admin", "game-admin");
        user = token("User", "game-user");
    }

    @Test
    void configurationCountsBothSourcesWithoutDoubleCountingAndExcludesInvalidSources() {
        long level = level();
        long lesson = lesson(level);
        question(level, lesson, null, true);
        long source = example();
        question(level, lesson, source, true);
        question(level, lesson, null, false);
        long archived = question(level, lesson, null, true);
        lifecycle(archived, "archive");
        Response config = config();
        assertEquals(2, config.jsonPath().getInt("data.levels.find { it.id == " + level + " }.questionCount"));
        assertEquals(2, config.jsonPath().getInt("data.lessons.find { it.id == " + lesson + " }.questionCount"));
        sql("UPDATE example_sentences SET japanese_reading=CONCAT(japanese_reading,'.'), "
                + "updated_at=DATE_ADD(updated_at, INTERVAL 1 SECOND) WHERE id=" + source);
        assertEquals(1, config().jsonPath().getInt("data.levels.find { it.id == " + level + " }.questionCount"));
        sql("DELETE FROM example_sentences WHERE id=" + source);
        assertEquals(1, config().jsonPath().getInt("data.lessons.find { it.id == " + lesson + " }.questionCount"));
        config.then().body("data.maxQuestionCount", equalTo(100));
    }

    @Test
    void unclassifiedQuestionsCountOnlyInTotalAndEmptyChoicesAreOmitted() {
        long emptyLevel = level();
        long emptyLesson = lesson(emptyLevel);
        long before = config().jsonPath().getLong("data.totalQuestions");
        question(null, null, null, true);
        Response response = config();
        assertEquals(before + 1, response.jsonPath().getLong("data.totalQuestions"));
        assertFalse(response.jsonPath().getList("data.levels.id", Long.class).contains(emptyLevel));
        assertFalse(response.jsonPath().getList("data.lessons.id", Long.class).contains(emptyLesson));
        create(user, Map.of("questionCount", 1)).then().statusCode(200);
    }

    @Test
    void filtersUseExplicitOrLessonLevelsAndIntersectSelectedLesson() {
        long level = level();
        long lesson = lesson(level);
        long other = lesson(level);
        long explicit = question(level, null, null, true);
        long implied = question(null, lesson, null, true);
        long another = question(null, other, null, true);
        long game = create(user, Map.of("levelId", level, "questionCount", 3)).then().statusCode(200)
                .extract().jsonPath().getLong("data.sessionId");
        assertEquals(Set.of(explicit, implied, another), new HashSet<>(numbers(
                "SELECT question_id FROM quiz_session_questions WHERE session_id=" + game)));
        long filtered = create(user, Map.of("levelId", level, "lessonId", lesson, "questionCount", 1))
                .then().statusCode(200).extract().jsonPath().getLong("data.sessionId");
        assertEquals(List.of(implied), numbers("SELECT question_id FROM quiz_session_questions WHERE session_id="
                + filtered));
    }

    @Test
    void snapshotsRemainIndependentAndResponseNeverExposesAnswers() {
        long lesson = lesson(level());
        long source = example();
        long question = question(null, lesson, source, true);
        Response response = create(user, Map.of("lessonId", lesson, "questionCount", 1));
        response.then().statusCode(200).body("data.status", equalTo("IN_PROGRESS"));
        Map<String, Object> data = response.jsonPath().getMap("data");
        assertEquals(Set.of("sessionId", "status", "questionCount", "createdAt"), data.keySet());
        long session = response.jsonPath().getLong("data.sessionId");
        long snapshot = number("SELECT id FROM quiz_session_questions WHERE session_id=" + session);
        assertEquals(4, number("SELECT COUNT(*) FROM quiz_session_options WHERE session_question_id=" + snapshot));
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_options WHERE is_correct=1 "
                + "AND session_question_id=" + snapshot));
        assertEquals(READING, text("SELECT sentence_reading FROM quiz_session_questions WHERE id=" + snapshot));
        lifecycle(question, "archive");
        sql("DELETE FROM example_sentences WHERE id=" + source);
        assertEquals(READING, text("SELECT sentence_reading FROM quiz_session_questions WHERE id=" + snapshot));
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_options WHERE is_correct=1 "
                + "AND session_question_id=" + snapshot));
        create(user, Map.of("lessonId", lesson, "questionCount", 1)).then().statusCode(409)
                .body("error.code", equalTo("QUIZ_INSUFFICIENT_QUESTIONS"));
    }

    @Test
    void selectionAndEachOptionSetAreRandomizedAndPersisted() {
        long lesson = lesson(level());
        for (int i = 0; i < 6; i++) {
            question(null, lesson, null, true);
        }
        Set<List<Long>> orders = new HashSet<>();
        Set<String> firstChoices = new HashSet<>();
        for (int i = 0; i < 12; i++) {
            long game = create(user, Map.of("lessonId", lesson, "questionCount", 3)).then().statusCode(200)
                    .extract().jsonPath().getLong("data.sessionId");
            List<Long> selected = numbers("SELECT question_id FROM quiz_session_questions WHERE session_id="
                    + game + " ORDER BY question_number");
            assertEquals(3, new HashSet<>(selected).size());
            orders.add(selected);
            for (long snapshot : numbers("SELECT id FROM quiz_session_questions WHERE session_id=" + game)) {
                firstChoices.add(text("SELECT option_text FROM quiz_session_options WHERE session_question_id="
                        + snapshot + " ORDER BY id LIMIT 1"));
                assertEquals(4, number("SELECT COUNT(*) FROM quiz_session_options WHERE session_question_id="
                        + snapshot));
            }
        }
        assertTrue(orders.size() > 1, "Repeated games must not always take the same ID-ordered prefix");
        assertTrue(firstChoices.size() > 1, "Option order must vary independently of bank order");
    }

    @Test
    void ownerComesOnlyFromCaseSensitiveJwtSubject() throws Exception {
        long lesson = lesson(level());
        question(null, lesson, null, true);
        for (String subject : List.of("Quiz-Owner", "quiz-owner")) {
            long id = create(token("User", subject), Map.of("lessonId", lesson, "questionCount", 1,
                    "userId", "attacker", "userSubject", "attacker"))
                    .then().statusCode(200).extract().jsonPath().getLong("data.sessionId");
            assertEquals(subject, text("SELECT user_subject FROM quiz_sessions WHERE id=" + id));
        }
        create(token("Admin", "game-admin"), Map.of("lessonId", lesson, "questionCount", 1))
                .then().statusCode(200);
    }

    @Test
    void rejectsInvalidFiltersCountsAndInsufficientQuestionsWithoutSessions() {
        long level = level();
        long lesson = lesson(level);
        long before = number("SELECT COUNT(*) FROM quiz_sessions");
        for (Map<String, Object> request : List.<Map<String, Object>>of(
                Map.of(), Map.of("questionCount", 0), Map.of("questionCount", 101),
                Map.of("questionCount", 1, "levelId", -1),
                Map.of("questionCount", 1, "levelId", Long.MAX_VALUE),
                Map.of("questionCount", 1, "lessonId", Long.MAX_VALUE),
                Map.of("questionCount", 1, "levelId", level(), "lessonId", lesson)
        )) {
            create(user, request).then().statusCode(400);
        }
        create(user, Map.of("lessonId", lesson, "questionCount", 1)).then().statusCode(409)
                .body("error.code", equalTo("QUIZ_INSUFFICIENT_QUESTIONS"));
        assertEquals(before, number("SELECT COUNT(*) FROM quiz_sessions"));
    }

    @Test
    void rollbackRemovesSessionAndPartiallyInsertedSnapshots() {
        long lesson = lesson(level());
        question(null, lesson, null, true);
        long sessions = number("SELECT COUNT(*) FROM quiz_sessions");
        long snapshots = number("SELECT COUNT(*) FROM quiz_session_questions");
        long options = number("SELECT COUNT(*) FROM quiz_session_options");
        long existingSnapshot = number("SELECT COALESCE(MAX(id),0) FROM quiz_session_questions");
        sql("ALTER TABLE quiz_session_options ADD CONSTRAINT ck_game_test_failure CHECK (session_question_id <= "
                + existingSnapshot + " OR option_text <> 'choice-2')");
        try {
            create(user, Map.of("lessonId", lesson, "questionCount", 1)).then().statusCode(409)
                    .body("error.code", equalTo("QUIZ_GAME_CONFLICT"));
            assertEquals(sessions, number("SELECT COUNT(*) FROM quiz_sessions"));
            assertEquals(snapshots, number("SELECT COUNT(*) FROM quiz_session_questions"));
            assertEquals(options, number("SELECT COUNT(*) FROM quiz_session_options"));
        } finally {
            sql("ALTER TABLE quiz_session_options DROP CHECK ck_game_test_failure");
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"classification", "status", "source"})
    void concurrentChangesAreRecheckedAfterQuestionLock(String change) throws Exception {
        long lesson = lesson(level());
        long source = example();
        long question = question(null, lesson, source, true);
        long before = number("SELECT COUNT(*) FROM quiz_sessions");
        var connection = pool.getConnection().await().atMost(TIMEOUT);
        var transaction = connection.begin().await().atMost(TIMEOUT);
        try {
            connection.query("SELECT id FROM quiz_questions WHERE id=" + question + " FOR UPDATE")
                    .execute().await().atMost(TIMEOUT);
            String mutation = switch (change) {
                case "classification" -> "DELETE FROM quiz_question_lessons WHERE question_id=" + question;
                case "status" -> "UPDATE quiz_questions SET status='DRAFT' WHERE id=" + question;
                default -> "UPDATE example_sentences SET japanese_reading=CONCAT(japanese_reading,'.'), "
                        + "updated_at=DATE_ADD(updated_at, INTERVAL 1 SECOND) WHERE id=" + source;
            };
            connection.query(mutation).execute().await().atMost(TIMEOUT);
            var pending = CompletableFuture.supplyAsync(() ->
                    create(user, Map.of("lessonId", lesson, "questionCount", 1)));
            long deadline = System.nanoTime() + TIMEOUT.toNanos();
            while (number("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'") == 0
                    && System.nanoTime() < deadline) {
                Thread.sleep(50);
            }
            assertTrue(number("SELECT COUNT(*) FROM information_schema.innodb_trx WHERE trx_state='LOCK WAIT'") > 0,
                    "Reproduction must reach a database lock wait before the mutation commits");
            assertFalse(pending.isDone(), "Session creation must wait for the question lock");
            transaction.commit().await().atMost(TIMEOUT);
            pending.get(15, TimeUnit.SECONDS).then().statusCode(409)
                    .body("error.code", equalTo("QUIZ_GAME_CONFLICT"));
            assertEquals(before, number("SELECT COUNT(*) FROM quiz_sessions"));
        } finally {
            connection.close().await().atMost(TIMEOUT);
        }
    }

    @Test
    void simultaneousSessionsRemainCompleteAndIndependent() throws Exception {
        long lesson = lesson(level());
        question(null, lesson, null, true);
        var first = CompletableFuture.supplyAsync(() ->
                create(user, Map.of("lessonId", lesson, "questionCount", 1)));
        var second = CompletableFuture.supplyAsync(() ->
                create(admin, Map.of("lessonId", lesson, "questionCount", 1)));
        Set<Long> ids = new HashSet<>();
        for (Response response : List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS))) {
            long id = response.then().statusCode(200).extract().jsonPath().getLong("data.sessionId");
            ids.add(id);
            assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_questions WHERE session_id=" + id));
        }
        assertEquals(2, ids.size());
    }

    @Test
    void bothEndpointsRequireUserOrAdminAndSessionRequiresUsableSubject() throws Exception {
        given().get(BASE + "/config").then().statusCode(401);
        given().contentType("application/json").body(Map.of("questionCount", 1))
                .post(BASE + "/sessions").then().statusCode(401);
        for (String role : List.of("Guest", "user", "admin")) {
            String rejected = token(role, "some-user");
            given().auth().oauth2(rejected).get(BASE + "/config").then().statusCode(403);
            create(rejected, Map.of("questionCount", 1)).then().statusCode(403);
        }
        for (String subject : List.of(" ", "x".repeat(256))) {
            create(token("User", subject), Map.of("questionCount", 1)).then().statusCode(401);
        }
        given().auth().oauth2(admin).get(BASE + "/config").then().statusCode(200);
    }

    private Response config() {
        Response response = given().auth().oauth2(user).get(BASE + "/config");
        response.then().statusCode(200);
        return response;
    }

    private Response create(String token, Map<String, Object> request) {
        return given().auth().oauth2(token).contentType("application/json").body(request).post(BASE + "/sessions");
    }

    private long question(Long level, Long lesson, Long example, boolean published) {
        Map<String, Object> content = new HashMap<>();
        content.put("sourceType", example == null ? "CUSTOM" : "EXAMPLE");
        if (example == null) {
            content.put("sentenceReading", READING + UUID.randomUUID());
        } else {
            content.put("exampleId", example);
        }
        content.put("targetStart", 0);
        content.put("targetLength", 4);
        content.put("targetReading", READING);
        content.put("levelIds", level == null ? List.of() : List.of(level));
        content.put("lessonIds", lesson == null ? List.of() : List.of(lesson));
        List<Map<String, Object>> options = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            options.add(Map.of("text", "choice-" + i, "correct", i == 0));
        }
        long id = given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("content", content, "options", options)).post(ADMIN)
                .then().statusCode(200).extract().jsonPath().getLong("data.id");
        if (published) {
            lifecycle(id, "publish");
        }
        return id;
    }

    private void lifecycle(long id, String action) {
        long version = given().auth().oauth2(admin).get(ADMIN + "/" + id).then().statusCode(200)
                .extract().jsonPath().getLong("data.version");
        given().auth().oauth2(admin).contentType("application/json").body(Map.of("version", version))
                .post(ADMIN + "/" + id + "/" + action).then().statusCode(200);
    }

    private String token(String role, String subject) throws Exception {
        var tokens = new JwtTestTokens(signingKey);
        var claims = tokens.claims(role);
        claims.setSubject(subject);
        return tokens.sign(claims);
    }

    private long level() {
        String code = UUID.randomUUID().toString().substring(0, 8);
        long order = number("SELECT MAX(display_order) FROM jlpt_levels") + 1;
        sql("INSERT INTO jlpt_levels(code,name,display_order) VALUES ('" + code + "','Game test'," + order + ")");
        return number("SELECT id FROM jlpt_levels WHERE code='" + code + "'");
    }

    private long lesson(long level) {
        long order = number("SELECT COUNT(*) FROM lessons WHERE level_id=" + level) + 1;
        sql("INSERT INTO lessons(level_id,lesson_number,title,display_order) VALUES (" + level
                + "," + order + ",'Game test'," + order + ")");
        return number("SELECT MAX(id) FROM lessons WHERE level_id=" + level);
    }

    private long example() {
        String marker = UUID.randomUUID().toString();
        sql("INSERT INTO example_sentences(japanese_text,japanese_reading,meaning_vi,meaning_en) VALUES ('"
                + marker + "','" + READING + "','vi','en')");
        return number("SELECT id FROM example_sentences WHERE japanese_text='" + marker + "'");
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
        List<Long> result = new ArrayList<>();
        pool.query(query).execute().await().atMost(TIMEOUT).forEach(row -> result.add(row.getLong(0)));
        return result;
    }
}
