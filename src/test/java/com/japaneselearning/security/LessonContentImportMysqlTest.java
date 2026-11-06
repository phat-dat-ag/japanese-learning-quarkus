package com.japaneselearning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
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

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class LessonContentImportMysqlTest {
    @Inject
    MySQLPool pool;

    @Inject
    ObjectMapper json;

    RsaJsonWebKey signingKey;

    String admin;

    static final String ROUTE = "/api/v1/lessons/content/import";

    @BeforeEach
    void token() throws Exception {
        admin = new JwtTestTokens(signingKey).token("Admin");
    }

    @Test
    void createsCompleteLessonsAndDefaultRetriesSkipWithoutOverwriting() throws Exception {
        int number = next();
        var file = List.of(item(number), item(number + 1));
        var report = upload(file, "SKIP_EXISTING");
        report.then().statusCode(200).body("data.created", equalTo(2), "data.failed", equalTo(0));
        long id = report.jsonPath().getLong("data.results[0].lessonId");
        given().auth().oauth2(admin).get("/api/v1/lessons/" + id + "/content").then().statusCode(200)
                .body("data.version", equalTo(1), "data.content.blocks[0].inlines[0].text", equalTo("日本語"));
        file.get(0).put("title", "Must not overwrite");
        upload(file, "SKIP_EXISTING").then().statusCode(200).body("data.skipped", equalTo(2));
        given().auth().oauth2(admin).get("/api/v1/lessons/" + id).then().statusCode(200).body("data.title", equalTo("Grammar import"));
        var updated = item(number);
        var content = given().auth().oauth2(admin).get("/api/v1/lessons/" + id + "/content");
        updated.put("expectedVersion", 1);
        updated.put("etag", content.header("ETag"));
        updated.put("title", "Updated grammar");
        upload(List.of(updated), "UPDATE_EXISTING").then().statusCode(200).body("data.updated", equalTo(1));
        upload(List.of(updated), "UPDATE_EXISTING").then().statusCode(200)
                .body("data.failed", equalTo(1), "data.results[0].error.code", equalTo("LESSON_CONTENT_CONFLICT"));
        assertEquals(2, query("SELECT version FROM lesson_contents WHERE lesson_id=" + id));
    }

    @Test
    void fullFileValidationRejectsDuplicatesMalformedContentAndUnknownLevelsBeforeAnyWrites() throws Exception {
        int number = next();
        var valid = item(number);
        var invalid = item(number + 1);
        invalid.put("content", Map.of("blocks", List.of(Map.of("id", "x", "type", "image"))));
        upload(List.of(valid, invalid), "SKIP_EXISTING").then().statusCode(400);
        invalid = item(number + 1);
        invalid.put("level", "N0");
        upload(List.of(valid, invalid), "SKIP_EXISTING").then().statusCode(404).body("error.code", equalTo("JLPT_LEVEL_NOT_FOUND"));
        upload(List.of(valid, valid), "SKIP_EXISTING").then().statusCode(400);
        invalid = item(number + 1);
        invalid.put("displayOrder", number);
        upload(List.of(valid, invalid), "SKIP_EXISTING").then().statusCode(400);
        assertEquals(0, query("SELECT COUNT(*) FROM lessons WHERE level_id=1 AND lesson_number=" + number));
        given().auth().oauth2(admin).multiPart("file", "bad.json", "[{}".getBytes(StandardCharsets.UTF_8), "application/json")
                .post(ROUTE).then().statusCode(400);
    }

    @Test
    void itemFailureRollsBackMetadataButOtherLessonsCommit() throws Exception {
        int number = next();
        var initial = upload(List.of(item(number)), "SKIP_EXISTING");
        long id = initial.jsonPath().getLong("data.results[0].lessonId");
        var stale = item(number);
        stale.put("title", "Rolled back metadata");
        var report = upload(List.of(stale, item(number + 1)), "UPDATE_EXISTING");
        report.then().statusCode(200).body("data.failed", equalTo(1), "data.created", equalTo(1),
                "data.results[0].status", equalTo("FAILED"), "data.results[1].status", equalTo("CREATED"));
        given().auth().oauth2(admin).get("/api/v1/lessons/" + id).then().statusCode(200).body("data.title", equalTo("Grammar import"));
        assertEquals(1, query("SELECT version FROM lesson_contents WHERE lesson_id=" + id));
        // A content precondition that cannot create must also roll back the freshly inserted parent.
        var impossible = item(number + 2);
        impossible.put("expectedVersion", 9);
        upload(List.of(impossible), "UPDATE_EXISTING").then().statusCode(200).body("data.failed", equalTo(1));
        assertEquals(0, query("SELECT COUNT(*) FROM lessons WHERE level_id=1 AND lesson_number=" + (number + 2)));
    }

    @Test
    void metadataOnlyLessonsAreSkippedByDefaultAndCanReceiveContentExplicitly() throws Exception {
        int number = next();
        long id = given().auth().oauth2(admin).contentType("application/json")
                .body(Map.of("levelId", 1, "lessonNumber", number, "title", "Metadata only", "displayOrder", number))
                .post("/api/v1/lessons").then().statusCode(200).extract().jsonPath().getLong("data.id");
        upload(List.of(item(number)), "SKIP_EXISTING").then().statusCode(200).body("data.skipped", equalTo(1));
        assertEquals(0, query("SELECT COUNT(*) FROM lesson_contents WHERE lesson_id=" + id));
        upload(List.of(item(number)), "UPDATE_EXISTING").then().statusCode(200).body("data.updated", equalTo(1));
        assertEquals(1, query("SELECT version FROM lesson_contents WHERE lesson_id=" + id));
    }

    @Test
    void importBusinessSuccessFollowsCommitAndRollbackHasNoSuccessEvent() throws Exception {
        int number = next();
        String correlation = java.util.UUID.randomUUID().toString();
        var events = new java.util.concurrent.CopyOnWriteArrayList<String>();
        var activeTransactions = new java.util.concurrent.CopyOnWriteArrayList<Boolean>();
        var logger = java.util.logging.Logger.getLogger(com.japaneselearning.common.logging.OperationLog.class.getName());
        var handler = new java.util.logging.Handler() {
            @Override
            public void publish(java.util.logging.LogRecord record) {
                var event = (org.jboss.logmanager.ExtLogRecord) record;
                if (correlation.equals(event.getMdc("correlationId")) && "success".equals(event.getMdc("outcome"))) {
                    var session = io.quarkus.hibernate.reactive.panache.common.runtime.SessionOperations.getCurrentSession("<default>");
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
            var failing = item(number);
            failing.put("expectedVersion", 9);
            given().auth().oauth2(admin).header("X-Correlation-ID", correlation)
                    .multiPart("file", "lessons.json", json.writeValueAsBytes(List.of(failing, item(number + 1))), "application/json")
                    .post(ROUTE + "?mode=UPDATE_EXISTING").then().statusCode(200)
                    .body("data.created", equalTo(1), "data.failed", equalTo(1));
            assertEquals(List.of("lesson.content.import.item.created"), events);
            assertEquals(List.of(false), activeTransactions);
            assertEquals(0, query("SELECT COUNT(*) FROM lessons WHERE level_id=1 AND lesson_number=" + number));
        } finally {
            logger.removeHandler(handler);
        }
    }

    @Test
    void concurrentImportsHaveOneCreatorAndOneSkipOrConflict() throws Exception {
        int number = next();
        var file = List.of(item(number));
        var first = CompletableFuture.supplyAsync(() -> uncheckedUpload(file));
        var second = CompletableFuture.supplyAsync(() -> uncheckedUpload(file));
        var a = first.get(25, TimeUnit.SECONDS);
        var b = second.get(25, TimeUnit.SECONDS);
        a.then().statusCode(200);
        b.then().statusCode(200);
        assertEquals(1, a.jsonPath().getInt("data.created") + b.jsonPath().getInt("data.created"));
        assertEquals(1, a.jsonPath().getInt("data.skipped") + b.jsonPath().getInt("data.skipped"));
        assertEquals(1, query("SELECT COUNT(*) FROM lessons WHERE level_id=1 AND lesson_number=" + number));
    }

    @Test
    void importRequiresAdminAndEnforcesCountAndFileSizeLimits() throws Exception {
        byte[] file = json.writeValueAsBytes(List.of(item(next())));
        given().multiPart("file", "lessons.json", file, "application/json").post(ROUTE).then().statusCode(401);
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .multiPart("file", "lessons.json", file, "application/json").post(ROUTE).then().statusCode(403);
        List<Map<String, Object>> items = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            items.add(item(next()));
        }
        upload(items, "SKIP_EXISTING").then().statusCode(400);
        given().auth().oauth2(admin).multiPart("file", "large.json", new byte[5242881], "application/json")
                .post(ROUTE).then().statusCode(413);
        given().auth().oauth2(admin).multiPart("file", "empty.json", new byte[0], "application/json")
                .post(ROUTE).then().statusCode(400);
        given().auth().oauth2(admin).multiPart("file", "lessons.json", file, "application/json")
                .post(ROUTE + "?mode=REPLACE").then().statusCode(404);
    }

    private int next() {
        return ThreadLocalRandom.current().nextInt(1000000, 900000000);
    }

    private Map<String, Object> item(int number) {
        var item = new HashMap<String, Object>();
        item.put("level", "N5");
        item.put("lessonNumber", number);
        item.put("title", "Grammar import");
        item.put("description", null);
        item.put("displayOrder", number);
        item.put("schemaVersion", 1);
        item.put("expectedVersion", 0);
        item.put("content", Map.of("blocks", List.of(Map.of("id", "p", "type", "paragraph",
                "inlines", List.of(Map.of("text", "日本語", "marks", List.of("bold")))))));
        return item;
    }

    private Response upload(Object items, String mode) throws Exception {
        return given().auth().oauth2(admin).multiPart("file", "lessons.json", json.writeValueAsBytes(items), "application/json")
                .post(ROUTE + "?mode=" + mode);
    }

    private Response uncheckedUpload(Object file) {
        try {
            return upload(file, "SKIP_EXISTING");
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        }
    }

    private long query(String sql) {
        return pool.query(sql).execute().await().atMost(Duration.ofSeconds(10)).iterator().next().getLong(0);
    }
}
