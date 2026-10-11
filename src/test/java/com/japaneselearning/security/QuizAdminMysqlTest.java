package com.japaneselearning.security;

import com.japaneselearning.quiz.service.QuizSessionSnapshotService;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.vertx.VertxContextSupport;
import io.restassured.path.json.JsonPath;
import io.restassured.response.Response;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizAdminMysqlTest {
    private static final String BASE = "/api/v1/admin/kanji-quiz/questions";
    private static final String READING = "\u304c\u3063\u3053\u3046";

    @Inject
    MySQLPool pool;

    @Inject
    QuizSessionSnapshotService snapshotService;

    RsaJsonWebKey signingKey;
    private String token;

    @BeforeEach
    void authenticate() throws Exception {
        token = new JwtTestTokens(signingKey).token("Admin");
    }

    @ParameterizedTest
    @CsvSource({
            "GET,''", "POST,''", "GET,/1", "PUT,/1", "PUT,/1/options/1",
            "PUT,/1/correct-option", "POST,/1/publish", "POST,/1/unpublish", "POST,/1/archive"
    })
    void everyOperationRequiresAdmin(String method, String suffix) throws Exception {
        given().contentType("application/json").body("{}")
                .request(method, BASE + suffix).then().statusCode(401);
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .contentType("application/json").body("{}")
                .request(method, BASE + suffix).then().statusCode(403);
    }

    @Test
    void customCrudOptionsAndLifecyclePreserveStableIds() throws Throwable {
        Map<String, Object> content = customContent();
        JsonPath created = create(content);
        long id = created.getLong("data.id");
        assertEquals("DRAFT", created.getString("data.status"));
        assertFalse(created.getBoolean("data.eligibleForNewGames"));
        List<Long> ids = optionIds(created);
        assertEquals(4, ids.size());
        JsonPath published = lifecycle(id, "publish", created);
        assertTrue(published.getBoolean("data.eligibleForNewGames"));
        var session = VertxContextSupport.subscribeAndAwait(() ->
                snapshotService.create("quiz-admin-options-test", List.of(id)));
        JsonPath edited = send("PUT", "/" + id + "/options/" + ids.get(1),
                Map.of("version", version(published), "text", "\u5927\u5b66"))
                .then().statusCode(200).extract().jsonPath();
        assertEquals("DRAFT", edited.getString("data.status"));
        assertEquals(ids, optionIds(edited));
        JsonPath changed = send("PUT", "/" + id + "/correct-option",
                Map.of("version", version(edited), "optionId", ids.get(1)))
                .then().statusCode(200).extract().jsonPath();
        assertEquals(ids.get(1).longValue(), changed.getLong("data.options.find { it.correct }.id"));
        assertEquals(1, changed.getList("data.options.findAll { it.correct }").size());
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_options o "
                + "JOIN quiz_session_questions q ON q.id=o.session_question_id WHERE q.session_id="
                + session.id + " AND o.option_text='choice-0' AND o.is_correct=1"));
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_options o "
                + "JOIN quiz_session_questions q ON q.id=o.session_question_id WHERE q.session_id="
                + session.id + " AND o.option_text='choice-1' AND o.is_correct=0"));
        content.put("explanationEn", "Updated explanation");
        JsonPath updated = send("PUT", "/" + id, Map.of("version", version(changed), "content", content))
                .then().statusCode(200).extract().jsonPath();
        assertEquals("Updated explanation", updated.getString("data.explanationEn"));
        assertEquals(ids, optionIds(updated));
        JsonPath archived = lifecycle(id, "archive", lifecycle(id, "publish", updated));
        assertEquals("ARCHIVED", archived.getString("data.status"));
        send("POST", "/" + id + "/publish", Map.of("version", version(archived)))
                .then().statusCode(409);
        JsonPath restored = lifecycle(id, "unpublish", archived);
        assertEquals("DRAFT", restored.getString("data.status"));
        send("GET", "/" + id, null).then().statusCode(200).body("data.status", equalTo("DRAFT"));
    }

    @Test
    void exampleInvalidationRevalidationDeletionAndSnapshotsRemainSafe() throws Throwable {
        String suffix = UUID.randomUUID().toString();
        sql("INSERT INTO example_sentences(japanese_text,japanese_reading,meaning_vi,meaning_en) VALUES ('"
                + suffix + "','" + READING + "','vi','en')");
        long exampleId = number("SELECT id FROM example_sentences WHERE japanese_text='" + suffix + "'");
        Map<String, Object> content = customContent();
        content.remove("sentenceReading");
        content.put("sourceType", "EXAMPLE");
        content.put("exampleId", exampleId);
        JsonPath created = create(content);
        long id = created.getLong("data.id");
        JsonPath published = lifecycle(id, "publish", created);
        var session = VertxContextSupport.subscribeAndAwait(() ->
                snapshotService.create("quiz-admin-test", List.of(id)));
        sql("UPDATE example_sentences SET japanese_reading=CONCAT(japanese_reading,'.') WHERE id=" + exampleId);
        JsonPath invalidated = send("GET", "/" + id, null).then().statusCode(200).extract().jsonPath();
        assertTrue(invalidated.getBoolean("data.sourceInvalidated"));
        assertFalse(invalidated.getBoolean("data.eligibleForNewGames"));
        JsonPath revalidated = lifecycle(id, "publish", invalidated);
        assertTrue(revalidated.getBoolean("data.eligibleForNewGames"));
        content.put("sentenceReading", "forbidden");
        send("PUT", "/" + id, Map.of("version", version(revalidated), "content", content))
                .then().statusCode(400);
        assertEquals(version(revalidated), version(send("GET", "/" + id, null).jsonPath()));
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_questions WHERE session_id=" + session.id
                + " AND sentence_reading='" + READING + "'"));
        sql("DELETE FROM example_sentences WHERE id=" + exampleId);
        send("GET", "/" + id, null).then().statusCode(200)
                .body("data.sourceInvalidated", equalTo(true)).body("data.eligibleForNewGames", equalTo(false));
        send("POST", "/" + id + "/publish", Map.of("version", version(revalidated))).then().statusCode(400);
        assertEquals(1, number("SELECT COUNT(*) FROM quiz_session_questions WHERE session_id=" + session.id));
    }

    @Test
    void filtersAndPaginationCombineWithoutDuplicatingQuestions() {
        String marker = UUID.randomUUID().toString();
        long level = number("SELECT id FROM jlpt_levels WHERE code='N5'");
        long otherLevel = number("SELECT id FROM jlpt_levels WHERE code='N4'");
        sql("INSERT INTO vocabulary(word,normalized_word) VALUES ('" + marker + "','" + marker + "')");
        long vocabulary = number("SELECT id FROM vocabulary WHERE normalized_word='" + marker + "'");
        sql("INSERT INTO lessons(level_id,lesson_number,title,display_order) VALUES ("
                + level + "," + (100000 + vocabulary) + ",'" + marker + "'," + (100000 + vocabulary) + ")");
        long lesson = number("SELECT id FROM lessons WHERE title='" + marker + "'");
        Map<String, Object> content = customContent();
        content.put("explanationEn", marker);
        content.put("vocabularyId", vocabulary);
        content.put("levelIds", List.of(level));
        content.put("lessonIds", List.of(lesson));
        JsonPath question = create(content);
        given().auth().oauth2(token).queryParams(Map.of(
                        "sourceType", "CUSTOM", "status", "DRAFT", "levelId", level,
                        "lessonId", lesson, "vocabularyId", vocabulary, "keyword", marker, "size", 1))
                .get(BASE).then().statusCode(200).body("data.totalElements", equalTo(1))
                .body("data.items[0].id", equalTo(question.getInt("data.id")));
        given().auth().oauth2(token).queryParams("keyword", marker, "size", 1, "page", 1)
                .get(BASE).then().statusCode(200).body("data.items.size()", equalTo(0))
                .body("data.totalPages", equalTo(1));
        given().auth().oauth2(token).queryParams("lessonId", lesson, "levelId", otherLevel)
                .get(BASE).then().statusCode(200).body("data.totalElements", equalTo(0));
        given().auth().oauth2(token).queryParam("exampleId", Long.MAX_VALUE)
                .get(BASE).then().statusCode(200).body("data.totalElements", equalTo(0));
    }

    @Test
    void ownershipErrorsAndInvalidWritesRollbackPublicationAndVersion() {
        JsonPath first = create(customContent());
        JsonPath other = create(customContent());
        long id = first.getLong("data.id");
        JsonPath published = lifecycle(id, "publish", first);
        send("PUT", "/" + id + "/options/" + optionIds(other).get(0),
                Map.of("version", version(published), "text", "replacement")).then().statusCode(404);
        send("PUT", "/" + id + "/correct-option",
                Map.of("version", version(published), "optionId", optionIds(other).get(0))).then().statusCode(404);
        Map<String, Object> invalid = customContent();
        invalid.put("targetStart", 999);
        send("PUT", "/" + id, Map.of("version", version(published), "content", invalid))
                .then().statusCode(400);
        JsonPath unchanged = send("GET", "/" + id, null).then().statusCode(200).extract().jsonPath();
        assertEquals(version(published), version(unchanged));
        assertEquals("PUBLISHED", unchanged.getString("data.status"));
        send("PUT", "/" + id + "/options/" + optionIds(first).get(1),
                Map.of("version", version(unchanged), "text", "choice-0")).then().statusCode(400);
        send("GET", "/" + Long.MAX_VALUE, null).then().statusCode(404);
    }

    @Test
    void duplicateCreatesAreAtomicAndDifferentTargetsAreAllowed() throws Exception {
        Map<String, Object> content = customContent();
        Map<String, Object> request = request(content);
        var first = CompletableFuture.supplyAsync(() -> send("POST", "", request).statusCode());
        var second = CompletableFuture.supplyAsync(() -> send("POST", "", request).statusCode());
        List<Integer> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertTrue(results.contains(200));
        assertTrue(results.contains(409));
        content.put("targetStart", 2);
        content.put("targetLength", 2);
        content.put("targetReading", "\u3053\u3046");
        create(content);
    }

    @Test
    void concurrentDraftOptionChangesRejectOneStaleVersion() throws Exception {
        JsonPath created = create(customContent());
        long id = created.getLong("data.id");
        long optionId = optionIds(created).get(1);
        var first = CompletableFuture.supplyAsync(() -> send("PUT", "/" + id + "/options/" + optionId,
                Map.of("version", version(created), "text", "first-change")).statusCode());
        var second = CompletableFuture.supplyAsync(() -> send("PUT", "/" + id + "/options/" + optionId,
                Map.of("version", version(created), "text", "second-change")).statusCode());
        List<Integer> results = List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        assertTrue(results.contains(200));
        assertTrue(results.contains(409));
        assertNotEquals(version(created), version(send("GET", "/" + id, null).jsonPath()));
    }

    @Test
    void validatesUnicodeOptionsReferencesAndPagination() {
        Map<String, Object> content = customContent();
        content.put("sentenceReading", "\ud83d\ude00" + content.get("sentenceReading"));
        content.put("targetStart", 1);
        create(content);
        content.put("targetStart", 2);
        send("POST", "", request(content)).then().statusCode(400);
        content = customContent();
        content.put("vocabularyId", Long.MAX_VALUE);
        send("POST", "", request(content)).then().statusCode(404);
        content = customContent();
        content.put("exampleId", 1);
        send("POST", "", request(content)).then().statusCode(400);
        Map<String, Object> invalidOptions = request(customContent());
        invalidOptions.put("options", List.of(Map.of("text", "one", "correct", true)));
        send("POST", "", invalidOptions).then().statusCode(400);
        invalidOptions.put("options", List.of(
                Map.of("text", "A", "correct", true), Map.of("text", "\uff21", "correct", false),
                Map.of("text", "B", "correct", false), Map.of("text", "C", "correct", false)));
        send("POST", "", invalidOptions).then().statusCode(400);
        for (Map<String, Object> filter : List.<Map<String, Object>>of(
                Map.of("sourceType", "BAD"), Map.of("status", "BAD"), Map.of("size", 101),
                Map.of("page", -1), Map.of("page", Integer.MAX_VALUE))) {
            given().auth().oauth2(token).queryParams(filter).get(BASE).then().statusCode(400);
        }
        send("POST", "", Map.of()).then().statusCode(400);
    }

    private Map<String, Object> customContent() {
        Map<String, Object> content = new HashMap<>();
        content.put("sourceType", "CUSTOM");
        content.put("sentenceReading", READING + UUID.randomUUID());
        content.put("targetStart", 0);
        content.put("targetLength", 4);
        content.put("targetReading", READING);
        content.put("levelIds", List.of());
        content.put("lessonIds", List.of());
        return content;
    }

    private Map<String, Object> request(Map<String, Object> content) {
        List<Map<String, Object>> options = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            options.add(Map.of("text", "choice-" + index, "correct", index == 0));
        }
        return new HashMap<>(Map.of("content", content, "options", options));
    }

    private JsonPath create(Map<String, Object> content) {
        return send("POST", "", request(content)).then().log().ifValidationFails().statusCode(200).extract().jsonPath();
    }

    private JsonPath lifecycle(long id, String action, JsonPath current) {
        return send("POST", "/" + id + "/" + action, Map.of("version", version(current)))
                .then().statusCode(200).extract().jsonPath();
    }

    private long version(JsonPath question) {
        return question.getLong("data.version");
    }

    private List<Long> optionIds(JsonPath question) {
        return question.getList("data.options.id", Long.class);
    }

    private Response send(String method, String suffix, Object body) {
        var request = given().auth().oauth2(token).contentType("application/json");
        if (body != null) {
            request.body(body);
        }
        return request.request(method, BASE + suffix);
    }

    private void sql(String query) {
        pool.query(query).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long number(String query) {
        return pool.query(query).execute().await().atMost(Duration.ofSeconds(10)).iterator().next().getLong(0);
    }
}
