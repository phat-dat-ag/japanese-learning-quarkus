package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class LessonBatchMysqlTest {
    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;
    private String token;
    private long levelId;
    private int number;

    @BeforeEach
    void setup() throws Exception {
        token = new JwtTestTokens(signingKey).token("Admin");
        levelId = queryLong("SELECT id FROM jlpt_levels WHERE code='N5'");
        number = ThreadLocalRandom.current().nextInt(1000000, 1000000000);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 3})
    void allItemsSucceedAndRemainPersisted(int size) {
        var requests = new ArrayList<Map<String, Object>>();
        for (int i = 0; i < size; i++) {
            requests.add(request(i));
        }
        var response = batch(requests, size);
        for (int i = 0; i < size; i++) {
            assertSuccess(response, i, i);
        }
    }

    @ParameterizedTest
    @CsvSource({"0,missing", "1,missing", "2,missing", "0,validation", "1,validation", "2,validation"})
    void failuresDoNotPreventOtherCommits(int failedIndex, String failure) {
        var requests = List.of(request(0), request(1), request(2));
        if (failure.equals("missing")) {
            requests.get(failedIndex).put("levelId", Long.MAX_VALUE);
        } else {
            requests.get(failedIndex).put("title", " ");
        }
        var response = batch(requests, 2);
        for (int i = 0; i < 3; i++) {
            if (i == failedIndex) {
                assertFailure(response, i, failure.equals("missing") ? "JLPT_LEVEL_NOT_FOUND" : "BAD_REQUEST");
                assertEquals(0, count(i));
            } else {
                assertSuccess(response, i, i);
            }
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"lessonNumber", "displayOrder"})
    void duplicateWithinBatchFailsOnlyConflictingItem(String field) {
        var requests = List.of(request(0), request(1), request(2));
        requests.get(1).put(field, number);
        var response = batch(requests, 2);
        assertSuccess(response, 0, 0);
        assertFailure(response, 1, "LESSON_CONFLICT");
        assertSuccess(response, 2, 2);
        assertEquals(2, queryLong("SELECT COUNT(*) FROM lessons WHERE level_id=" + levelId
                + " AND lesson_number BETWEEN " + number + " AND " + (number + 2)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lessonNumber", "displayOrder"})
    void existingConflictDoesNotOverwriteAndLaterItemSucceeds(String field) {
        long existing = given().auth().oauth2(token).contentType("application/json").body(request(0))
                .post("/api/v1/lessons").then().statusCode(200).extract().jsonPath().getLong("data.id");
        var conflict = request(1);
        conflict.put(field, number);
        conflict.put("title", "Must not overwrite");
        var response = batch(List.of(conflict, request(2)), 1);
        assertFailure(response, 0, "LESSON_CONFLICT");
        assertSuccess(response, 1, 2);
        assertEquals(1, queryLong("SELECT COUNT(*) FROM lessons WHERE id=" + existing
                + " AND title='Batch lesson' AND lesson_number=" + number + " AND display_order=" + number));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1})
    void databaseInsertFailureRollsBackOnlyThatItemAndClosesFailedSession(int failedIndex) {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        String trigger = "lesson_batch_fail_" + suffix;
        String marker = "failure-" + suffix;
        // Only the dedicated test database is allowed by VocabularyMysqlTestProfile.
        execute("CREATE TRIGGER " + trigger + " BEFORE INSERT ON lessons FOR EACH ROW BEGIN "
                + "IF NEW.title='" + marker + "' THEN SIGNAL SQLSTATE '45000' "
                + "SET MESSAGE_TEXT='private persistence failure'; END IF; END");
        try {
            var requests = List.of(request(0), request(1), request(2));
            requests.get(failedIndex).put("title", marker);
            var response = batch(requests, 2);
            assertFailure(response, failedIndex, "INTERNAL_SERVER_ERROR");
            assertEquals("An unexpected error occurred", response.getString(
                    "data.results[" + failedIndex + "].error.message"));
            assertFalse(response.prettify().contains("private persistence failure"));
            assertEquals(0, count(failedIndex));
            for (int i = 0; i < 3; i++) {
                if (i != failedIndex) {
                    assertSuccess(response, i, i);
                }
            }
            // The rolled-back item's unique values remain available on the next request.
            assertSuccess(batch(List.of(request(failedIndex)), 1), 0, failedIndex);
        } finally {
            execute("DROP TRIGGER " + trigger);
        }
    }

    @Test
    void allDtoConstraintsAreItemFailures() {
        var invalid = new ArrayList<Map<String, Object>>();
        Map<String, List<Object>> values = Map.of(
                "levelId", List.of(0, -1), "lessonNumber", List.of(0, -1),
                "displayOrder", List.of(0, -1), "title", List.of("", " ", "x".repeat(201)),
                "description", List.of("x".repeat(1001)));
        for (var field : values.entrySet()) {
            for (Object value : field.getValue()) {
                var item = request(0);
                item.put(field.getKey(), value);
                invalid.add(item);
            }
        }
        for (String field : List.of("levelId", "lessonNumber", "displayOrder", "title")) {
            var item = request(0);
            item.put(field, null);
            invalid.add(item);
        }
        invalid.add(null);
        var valid = request(0);
        valid.put("title", "x".repeat(200));
        valid.put("description", "x".repeat(1000));
        invalid.add(valid);
        var response = batch(invalid, 1);
        for (int i = 0; i < invalid.size() - 1; i++) {
            assertFailure(response, i, "BAD_REQUEST");
        }
        assertSuccess(response, invalid.size() - 1, 0);
    }

    private Map<String, Object> request(int offset) {
        var result = new HashMap<String, Object>(Map.of("levelId", levelId,
                "lessonNumber", number + offset, "displayOrder", number + offset, "title", "Batch lesson"));
        result.put("description", null);
        return result;
    }

    private JsonPath batch(List<Map<String, Object>> requests, int succeeded) {
        var response = given().auth().oauth2(token).contentType("application/json").body(requests)
                .post("/api/v1/lessons/batch").then().statusCode(200)
                .body("success", equalTo(true), "meta.traceId", notNullValue(),
                        "data.total", equalTo(requests.size()), "data.succeeded", equalTo(succeeded),
                        "data.failed", equalTo(requests.size() - succeeded))
                .extract().jsonPath();
        assertEquals(requests.size(), response.getList("data.results").size());
        for (int i = 0; i < requests.size(); i++) {
            assertEquals(i, response.getInt("data.results[" + i + "].index"));
        }
        return response;
    }

    private void assertSuccess(JsonPath response, int index, int offset) {
        String result = "data.results[" + index + "]";
        assertTrue(response.getBoolean(result + ".success"));
        assertNull(response.get(result + ".error"));
        assertEquals(number + offset, response.getInt(result + ".lesson.lessonNumber"));
        long id = response.getLong(result + ".lesson.id");
        assertEquals(1, queryLong("SELECT COUNT(*) FROM lessons WHERE id=" + id
                + " AND level_id=" + levelId + " AND lesson_number=" + (number + offset)
                + " AND display_order=" + (number + offset)));
    }

    private void assertFailure(JsonPath response, int index, String code) {
        String result = "data.results[" + index + "]";
        assertFalse(response.getBoolean(result + ".success"));
        assertNull(response.get(result + ".lesson"));
        assertEquals(code, response.getString(result + ".error.code"));
        assertEquals(List.of(), response.getList(result + ".error.details"));
    }

    private long count(int offset) {
        return queryLong("SELECT COUNT(*) FROM lessons WHERE level_id=" + levelId
                + " AND lesson_number=" + (number + offset));
    }

    private void execute(String sql) {
        pool.query(sql).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long queryLong(String sql) {
        return pool.query(sql).execute().await().atMost(Duration.ofSeconds(10)).iterator().next().getLong(0);
    }
}
