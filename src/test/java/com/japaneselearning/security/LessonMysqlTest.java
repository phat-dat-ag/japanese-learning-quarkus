package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class LessonMysqlTest {
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

    @Test
    void createsAtExactLengthLimitsAndGetReturnsPersistedLesson() {
        var body = request(levelId, number, number);
        body.put("title", "x".repeat(200));
        body.put("description", "x".repeat(1000));
        long id = create(body);
        assertEquals(200, queryLong("SELECT CHAR_LENGTH(title) FROM lessons WHERE id=" + id));
        assertEquals(1000, queryLong("SELECT CHAR_LENGTH(description) FROM lessons WHERE id=" + id));
        assertEquals(1, queryLong("SELECT COUNT(*) FROM lessons WHERE id=" + id
                + " AND created_at IS NOT NULL AND updated_at IS NOT NULL"));
        given().auth().oauth2(token).get("/api/v1/lessons?level=N5").then().statusCode(200)
                .body("data.find { it.id == " + id + " }.lessonNumber", equalTo(number));
        body.put("lessonNumber", number + 1);
        body.put("displayOrder", number + 1);
        body.remove("description");
        long withoutDescription = create(body);
        assertEquals(1, queryLong("SELECT COUNT(*) FROM lessons WHERE id=" + withoutDescription
                + " AND description IS NULL"));
    }

    @Test
    void updatesLevelAndFieldsWithoutChangingVocabularyAssignments() {
        long id = create(request(levelId, number, number));
        String word = "lesson-test-" + UUID.randomUUID();
        execute("INSERT INTO vocabulary(word,normalized_word) VALUES ('" + word + "','" + word + "')");
        long vocabularyId = queryLong("SELECT id FROM vocabulary WHERE normalized_word='" + word + "'");
        execute("INSERT INTO lesson_vocabulary(lesson_id,vocabulary_id,display_order) VALUES ("
                + id + "," + vocabularyId + ",7)");
        long targetLevel = queryLong("SELECT id FROM jlpt_levels WHERE code='N4'");
        var body = request(targetLevel, number + 1, number + 2);
        body.put("title", "Updated");
        body.put("description", null);
        send("PUT", "/api/v1/lessons/" + id, body).statusCode(200)
                .body("data.id", equalTo((int) id), "data.title", equalTo("Updated"));
        send("PUT", "/api/v1/lessons/" + id, body).statusCode(200);
        assertEquals(targetLevel, queryLong("SELECT level_id FROM lessons WHERE id=" + id));
        assertEquals(number + 1, queryLong("SELECT lesson_number FROM lessons WHERE id=" + id));
        assertEquals(number + 2, queryLong("SELECT display_order FROM lessons WHERE id=" + id));
        assertEquals(1, queryLong("SELECT COUNT(*) FROM lessons WHERE id=" + id + " AND description IS NULL"));
        assertEquals(7, queryLong("SELECT display_order FROM lesson_vocabulary WHERE lesson_id=" + id
                + " AND vocabulary_id=" + vocabularyId));
    }

    @Test
    void missingLevelOrLessonReturns404WithoutChangingExistingLesson() {
        send("POST", "/api/v1/lessons", request(Long.MAX_VALUE, number, number)).statusCode(404);
        send("PUT", "/api/v1/lessons/" + Long.MAX_VALUE, request(levelId, number, number)).statusCode(404);
        long id = create(request(levelId, number, number));
        send("PUT", "/api/v1/lessons/" + id, request(Long.MAX_VALUE, number, number)).statusCode(404);
        assertEquals(levelId, queryLong("SELECT level_id FROM lessons WHERE id=" + id));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void duplicateNumberOrOrderReturns409AndRollsBack(boolean duplicateNumber) {
        create(request(levelId, number, number));
        long other = create(request(levelId, number + 1, number + 1));
        var body = request(levelId, duplicateNumber ? number : number + 2,
                duplicateNumber ? number + 2 : number);
        send("POST", "/api/v1/lessons", body).statusCode(409)
                .body("error.code", equalTo("LESSON_CONFLICT"));
        send("PUT", "/api/v1/lessons/" + other, body).statusCode(409);
        assertEquals(number + 1, queryLong("SELECT lesson_number FROM lessons WHERE id=" + other));
        assertEquals(number + 1, queryLong("SELECT display_order FROM lessons WHERE id=" + other));
    }

    private Map<String, Object> request(long level, int lessonNumber, int order) {
        return new HashMap<>(Map.of("levelId", level, "lessonNumber", lessonNumber,
                "title", "Lesson fixture", "description", "Description", "displayOrder", order));
    }

    private long create(Map<String, Object> body) {
        return send("POST", "/api/v1/lessons", body).statusCode(200)
                .body("success", equalTo(true), "meta.traceId", notNullValue())
                .extract().jsonPath().getLong("data.id");
    }

    private ValidatableResponse send(String method, String path, Map<String, Object> body) {
        return given().auth().oauth2(token).contentType("application/json").body(body)
                .request(method, path).then();
    }

    private void execute(String sql) {
        pool.query(sql).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long queryLong(String sql) {
        return pool.query(sql).execute().await().atMost(Duration.ofSeconds(10))
                .iterator().next().getLong(0);
    }
}
