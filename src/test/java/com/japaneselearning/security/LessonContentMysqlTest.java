package com.japaneselearning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.common.logging.OperationLog;
import io.quarkus.hibernate.reactive.panache.common.runtime.SessionOperations;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.Response;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.jose4j.jwk.RsaJsonWebKey;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.logging.Handler;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class LessonContentMysqlTest {
    @Inject
    MySQLPool pool;
    @Inject
    ObjectMapper json;
    RsaJsonWebKey signingKey;
    String admin;
    String user;

    @BeforeEach
    void tokens() throws Exception {
        admin = new JwtTestTokens(signingKey).token("Admin");
        user = new JwtTestTokens(signingKey).token("User");
    }

    @Test
    void metadataAndMultipleLessonOrderingKeepTheExistingContract() {
        int number = next();
        long first = create(number, number + 1);
        long second = create(number + 1, number);
        var lessons = given().auth().oauth2(user).get("/api/v1/lessons?level=N5")
                .then().statusCode(200).extract().jsonPath().getList("data.id", Long.class);
        assertTrue(lessons.indexOf(second) < lessons.indexOf(first));
        var response = given().auth().oauth2(user).get(path(first, false)).then().statusCode(200)
                .body("data.id", equalTo((int) first), "success", equalTo(true), "meta.traceId", notNullValue())
                .extract().jsonPath();
        assertEquals(java.util.Set.of("id", "lessonNumber", "title"), response.getMap("data").keySet());
        given().auth().oauth2(user).get(path(Long.MAX_VALUE, false)).then().statusCode(404)
                .body("error.code", equalTo("RESOURCE_NOT_FOUND"));
    }

    @Test
    void lifecyclePreservesUnicodeAndRequiresFreshRevisionAndGeneration() throws Exception {
        long id = create();
        given().auth().oauth2(user).get(path(id, true)).then().statusCode(404)
                .body("error.code", equalTo("LESSON_CONTENT_NOT_FOUND"));
        var content = json.readTree(getClass().getResourceAsStream("/lesson-content-v1.json"));
        Response created = put(id, 0, content, null);
        created.then().statusCode(200).body("data.version", equalTo(1), "data.schemaVersion", equalTo(1));
        String etag = created.header("ETag");
        assertNotNull(etag);
        given().auth().oauth2(user).get(path(id, true)).then().statusCode(200)
                .header("ETag", etag).body("data.content.blocks[5].japanese", equalTo("私は学生です。𠮷"));
        put(id, 0, Map.of("blocks", List.of()), null).then().statusCode(409);
        put(id, 1, Map.of("blocks", List.of()), null).then().statusCode(409);
        Response updated = put(id, 1, Map.of("blocks", List.of()), etag);
        updated.then().statusCode(200).body("data.version", equalTo(2), "data.content.blocks", hasSize(0));
        put(id, 1, content, etag).then().statusCode(409).body("error.code", equalTo("LESSON_CONTENT_CONFLICT"));
        removeContent(id, 1, etag).then().statusCode(409);
        removeContent(id, 2, updated.header("ETag")).then().statusCode(200).body("success", equalTo(true));
        removeContent(id, 2, updated.header("ETag")).then().statusCode(404);
        var recreated = put(id, 0, content, null);
        recreated.then().statusCode(200).body("data.version", equalTo(1));
        assertNotEquals(etag, recreated.header("ETag"));
        put(id, 1, content, etag).then().statusCode(409);
        removeContent(id, 1, etag).then().statusCode(409);
        assertEquals(1, count("SELECT COUNT(*) FROM lessons WHERE id=" + id));
    }

    @ParameterizedTest
    @ValueSource(strings = {"vocabulary", "quiz", "flashcard", "snapshot"})
    void deletingReferencedLessonsPreservesAssignmentsAndHistory(String reference) {
        long id = create();
        String marker = "grammar-" + UUID.randomUUID();
        switch (reference) {
            case "vocabulary" -> {
                sql("INSERT INTO vocabulary(word,normalized_word) VALUES ('" + marker + "','" + marker + "')");
                long vocabulary = count("SELECT id FROM vocabulary WHERE normalized_word='" + marker + "'");
                sql("INSERT INTO lesson_vocabulary(lesson_id,vocabulary_id,display_order) VALUES (" + id + "," + vocabulary + ",1)");
            }
            case "quiz" -> {
                sql("INSERT INTO quiz_questions(source_type,sentence_reading,target_start,target_length,target_reading,explanation_en) "
                        + "VALUES ('CUSTOM','あ',0,1,'あ','" + marker + "')");
                long question = count("SELECT id FROM quiz_questions WHERE explanation_en='" + marker + "'");
                sql("INSERT INTO quiz_question_lessons(question_id,lesson_id) VALUES (" + question + "," + id + ")");
            }
            case "flashcard" ->
                    sql("INSERT INTO flashcard_sessions(user_subject,lesson_id,mode,status,item_count,created_at,updated_at) "
                            + "VALUES ('" + marker + "'," + id + ",'ALL','IN_PROGRESS',1,UTC_TIMESTAMP(6),UTC_TIMESTAMP(6))");
            case "snapshot" -> {
                sql("INSERT INTO quiz_sessions(user_subject,question_count) VALUES ('" + marker + "',1)");
                long session = count("SELECT id FROM quiz_sessions WHERE user_subject='" + marker + "'");
                sql("INSERT INTO quiz_session_questions(session_id,question_version,question_number,source_type,sentence_reading,target_start,target_length,target_reading) "
                        + "VALUES (" + session + ",1,0,'CUSTOM','あ',0,1,'あ')");
                long snapshot = count("SELECT id FROM quiz_session_questions WHERE session_id=" + session);
                sql("INSERT INTO quiz_session_question_lessons(session_question_id,lesson_id,level_id) VALUES (" + snapshot + "," + id + ",1)");
            }
        }
        put(id, 0, Map.of("blocks", List.of()), null).then().statusCode(200);
        given().auth().oauth2(admin).delete(path(id, false)).then().statusCode(409).body("error.code", equalTo("LESSON_CONFLICT"));
        assertEquals(1, count("SELECT COUNT(*) FROM lessons WHERE id=" + id));
        assertEquals(1, count("SELECT COUNT(*) FROM lesson_contents WHERE lesson_id=" + id));
    }

    @Test
    void deletingUnreferencedLessonCascadesOnlyItsContent() {
        long id = create();
        put(id, 0, Map.of("blocks", List.of()), null).then().statusCode(200);
        given().auth().oauth2(admin).delete(path(id, false)).then().statusCode(200).body("data.id", equalTo((int) id));
        assertEquals(0, count("SELECT COUNT(*) FROM lesson_contents WHERE lesson_id=" + id));
        given().auth().oauth2(admin).delete(path(id, false)).then().statusCode(404);
    }

    @Test
    void contentDeletionPreservesVocabularyAssignments() {
        long id = create();
        String word = "grammar-content-delete-" + UUID.randomUUID();
        sql("INSERT INTO vocabulary(word,normalized_word) VALUES ('" + word + "','" + word + "')");
        long vocabulary = count("SELECT id FROM vocabulary WHERE normalized_word='" + word + "'");
        sql("INSERT INTO lesson_vocabulary(lesson_id,vocabulary_id,display_order) VALUES (" + id + "," + vocabulary + ",7)");
        var created = put(id, 0, Map.of("blocks", List.of()), null);
        created.then().statusCode(200);
        removeContent(id, 1, created.header("ETag")).then().statusCode(200);
        assertEquals(7, count("SELECT display_order FROM lesson_vocabulary WHERE lesson_id=" + id + " AND vocabulary_id=" + vocabulary));
        assertEquals(1, count("SELECT COUNT(*) FROM lessons WHERE id=" + id));
    }

    @Test
    void concurrentCreationAndUpdatesCommitExactlyOneWinner() throws Exception {
        long id = create();
        var empty = Map.of("blocks", List.of());
        var one = CompletableFuture.supplyAsync(() -> put(id, 0, empty, null));
        var two = CompletableFuture.supplyAsync(() -> put(id, 0, empty, null));
        var first = one.get(20, TimeUnit.SECONDS);
        var second = two.get(20, TimeUnit.SECONDS);
        assertEquals(java.util.Set.of(200, 409), java.util.Set.of(first.statusCode(), second.statusCode()));
        String etag = (first.statusCode() == 200 ? first : second).header("ETag");
        var three = CompletableFuture.supplyAsync(() -> put(id, 1, empty, etag).statusCode());
        var four = CompletableFuture.supplyAsync(() -> put(id, 1, empty, etag).statusCode());
        assertEquals(java.util.Set.of(200, 409), java.util.Set.of(three.get(20, TimeUnit.SECONDS), four.get(20, TimeUnit.SECONDS)));
        assertEquals(2, count("SELECT version FROM lesson_contents WHERE lesson_id=" + id));
    }

    @Test
    void validatesInputAndMissingParentWithoutMutating() {
        long id = create();
        for (String body : List.of("{", "null", "[]", "{}", "{\"schemaVersion\":1,\"expectedVersion\":0,\"content\":{\"blocks\":[]}} {}",
                "{\"schemaVersion\":1,\"expectedVersion\":0,\"unexpected\":true,\"content\":{\"blocks\":[]}}",
                "{\"schemaVersion\":1,\"expectedVersion\":0,\"content\":{\"blocks\":[{\"id\":\"x\",\"type\":\"image\"}]}}")) {
            given().auth().oauth2(admin).contentType("application/json").body(body).put(path(id, true)).then().statusCode(400);
        }
        given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("schemaVersion", 2, "expectedVersion", 0, "content", Map.of("blocks", List.of())))
                .put(path(id, true)).then().statusCode(400);
        put(Long.MAX_VALUE, 0, Map.of("blocks", List.of()), null).then().statusCode(404)
                .body("error.code", equalTo("RESOURCE_NOT_FOUND"));
        assertEquals(0, count("SELECT COUNT(*) FROM lesson_contents WHERE lesson_id=" + id));
        String huge = "{\"schemaVersion\":1,\"expectedVersion\":0,\"content\":{\"blocks\":[],\"extra\":\"" + "x".repeat(1048577) + "\"}}";
        given().auth().oauth2(admin).contentType("application/json").body(huge).put(path(id, true)).then().statusCode(413);
    }

    @Test
    void contentMutationBusinessSuccessFollowsCommit() {
        long id = create();
        String correlation = UUID.randomUUID().toString();
        List<Boolean> activeTransactions = new CopyOnWriteArrayList<>();
        List<String> events = new CopyOnWriteArrayList<>();
        Logger logger = Logger.getLogger(OperationLog.class.getName());
        Handler handler = new Handler() {
            @Override
            public void publish(LogRecord record) {
                var event = (ExtLogRecord) record;
                if (correlation.equals(event.getMdc("correlationId")) && "success".equals(event.getMdc("outcome"))) {
                    var session = SessionOperations.getCurrentSession("<default>");
                    activeTransactions.add(session != null && session.currentTransaction() != null);
                    events.add(event.getMdc("event.name"));
                }
            }

            @Override
            public void flush() {
            }

            @Override
            public void close() {
            }
        };
        logger.addHandler(handler);
        try {
            var created = given().auth().oauth2(admin).header("X-Correlation-ID", correlation)
                    .contentType("application/json").body(Map.of("schemaVersion", 1, "expectedVersion", 0, "content", Map.of("blocks", List.of())))
                    .put(path(id, true));
            created.then().statusCode(200);
            assertEquals(List.of("lesson.content.save.created"), events);
            assertEquals(List.of(false), activeTransactions);
            given().auth().oauth2(admin).header("X-Correlation-ID", correlation).header("If-Match", created.header("ETag"))
                    .delete(path(id, true) + "?expectedVersion=2").then().statusCode(409);
            assertEquals(1, events.size());
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void readAndWriteAuthorizationRemainDistinct() {
        for (String path : List.of(path(1, false), path(1, true))) {
            given().get(path).then().statusCode(401);
            given().delete(path).then().statusCode(401);
            given().auth().oauth2(user).delete(path).then().statusCode(403);
        }
        given().contentType("application/json").body("{}").put(path(1, true)).then().statusCode(401);
        given().auth().oauth2(user).contentType("application/json").body("{}").put(path(1, true)).then().statusCode(403);
    }

    private long create() {
        int number = next();
        return create(number, number);
    }

    private int next() {
        return ThreadLocalRandom.current().nextInt(1000000, 900000000);
    }

    private long create(int number, int order) {
        return given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("levelId", 1, "lessonNumber", number, "title", "Grammar fixture", "displayOrder", order))
                .post("/api/v1/lessons").then().statusCode(200).extract().jsonPath().getLong("data.id");
    }

    private String path(long id, boolean content) {
        return "/api/v1/lessons/" + id + (content ? "/content" : "");
    }

    private Response put(long id, long version, Object content, String etag) {
        var request = given().auth().oauth2(admin).contentType("application/json");
        if (etag != null) {
            request.header("If-Match", etag);
        }
        return request.body(Map.of("schemaVersion", 1, "expectedVersion", version, "content", content)).put(path(id, true));
    }

    private Response removeContent(long id, long version, String etag) {
        return given().auth().oauth2(admin).header("If-Match", etag).delete(path(id, true) + "?expectedVersion=" + version);
    }

    private void sql(String query) {
        pool.query(query).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long count(String query) {
        return pool.query(query).execute().await().atMost(Duration.ofSeconds(10)).iterator().next().getLong(0);
    }
}
