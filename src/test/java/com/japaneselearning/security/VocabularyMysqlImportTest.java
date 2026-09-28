package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import io.vertx.mutiny.mysqlclient.MySQLPool;
import jakarta.inject.Inject;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;

// Opt in only against a disposable MySQL database initialized with migrations V1 through V5.
@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class VocabularyMysqlImportTest {
    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;
    private String token;
    private String word;

    @BeforeEach
    void setUp() throws Exception {
        token = new JwtTestTokens(signingKey).token("Admin");
        word = "mysql-" + UUID.randomUUID();
        pool.query("""
                INSERT INTO lessons(level_id, lesson_number, title, display_order)
                SELECT id, 1, 'Test lesson 1', 1 FROM jlpt_levels WHERE code IN ('N5', 'N4')
                ON DUPLICATE KEY UPDATE title = title
                """).execute().await().atMost(Duration.ofSeconds(10));
        pool.query("""
                INSERT INTO lessons(level_id, lesson_number, title, display_order)
                SELECT id, 2, 'Test lesson 2', 2 FROM jlpt_levels WHERE code IN ('N5', 'N4')
                ON DUPLICATE KEY UPDATE title = title
                """).execute().await().atMost(Duration.ofSeconds(10));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedImportsAndChangedMetadataWriteOnlyNewExamples(boolean file) {
        String item = item(word);
        counts(submit(file, "[" + item + "]"), 1, 0);
        List<String> metadata = metadataSnapshot();
        long sentences = count("example_sentences");
        long links = count("vocabulary_examples");

        counts(submit(file, "[" + item + "]"), 0, 1);
        counts(submit(file, "[" + item + "]"), 0, 1);
        assertEquals(metadata, metadataSnapshot());
        assertEquals(sentences, count("example_sentences"));
        assertEquals(links, count("vocabulary_examples"));

        String changed = item.replace("\"word\":\"" + word + "\"", "\"word\":\"ignored edit\"")
                .replace("\"reading\":\"reading\"", "\"reading\":\"ignored reading\"")
                .replace("\"meaning\":\"meaning\"", "\"meaning\":\"ignored meaning\"")
                .replace("\"partsOfSpeech\":[\"NOUN\"]", "\"partsOfSpeech\":[\"VERB\"]")
                .replace("\"strokeCount\":11", "\"strokeCount\":12")
                .replace("\"accentPattern\":0", "\"accentPattern\":1")
                .replace("\"displayOrder\":1", "\"displayOrder\":9")
                .replace("\"examples\":[", "\"examples\":[" + example("new sentence", "target") + ",");
        counts(submit(file, "[" + changed + "]"), 0, 1);
        assertEquals(metadata, metadataSnapshot());
        assertEquals(sentences + 1, count("example_sentences"));
        assertEquals(links + 1, count("vocabulary_examples"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assignmentMismatchLeavesEarlierNewItemUncommitted(boolean file) {
        counts(submit(file, "[" + item(word) + "]"), 1, 0);
        List<String> before = metadataSnapshot();
        long examples = count("example_sentences");
        submit(file, "[" + item(word + "-new") + "," + item(word).replace("N5", "N4") + "]")
                .statusCode(400).body("error.code", equalTo("VALIDATION_ERROR"));
        submit(file, "[" + item(word).replace("\"lessonNumber\":1", "\"lessonNumber\":2") + "]")
                .statusCode(400).body("error.code", equalTo("VALIDATION_ERROR"));
        assertEquals(before, metadataSnapshot());
        assertEquals(examples, count("example_sentences"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exampleDatabaseFailureRollsBackEarlierWrites(boolean file) {
        counts(submit(file, "[" + item(word) + "]"), 1, 0);
        List<String> before = metadataSnapshot();
        long sentences = count("example_sentences");
        long links = count("vocabulary_examples");
        String invalidExample = item(word).replace("\"japaneseText\":\"sentence\"",
                "\"japaneseText\":\"" + "x".repeat(1001) + "\"");
        submit(file, "[" + item(word + "-new") + "," + invalidExample + "]")
                .statusCode(500).body("error.code", equalTo("INTERNAL_SERVER_ERROR"));
        assertEquals(before, metadataSnapshot());
        assertEquals(sentences, count("example_sentences"));
        assertEquals(links, count("vocabulary_examples"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixedBatchAndDuplicateExamplesHaveAccurateCounts(boolean file) {
        String duplicateExamples = item(word).replace("\"examples\":[",
                "\"examples\":[" + example("sentence", "target") + ",");
        long examples = count("example_sentences");
        counts(submit(file, "[" + duplicateExamples + "]"), 1, 0);
        assertEquals(examples + 1, count("example_sentences"));
        counts(submit(file, "[" + item(word + "-new") + "," + item(word) + "]"), 1, 1);
        assertEquals(examples + 2, count("example_sentences"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void exampleIdentityIgnoresTranslationsAndOrderButIncludesReadingAndTarget(boolean file) {
        String original = item(word);
        counts(submit(file, "[" + original + "]"), 1, 0);
        List<String> before = snapshot(List.of("example_sentences", "vocabulary_examples"));
        long examples = count("example_sentences");
        String translated = original.replace("\"meaningVi\":\"test\"", "\"meaningVi\":\"changed\"")
                .replace("\"meaningEn\":\"test\"", "\"meaningEn\":\"changed\"")
                .replace("\"displayOrder\":1", "\"displayOrder\":8");
        counts(submit(file, "[" + translated + "]"), 0, 1);
        assertEquals(before, snapshot(List.of("example_sentences", "vocabulary_examples")));
        counts(submit(file, "[" + original.replace("\"japaneseReading\":\"reading\"",
                "\"japaneseReading\":\"other reading\"") + "]"), 0, 1);
        counts(submit(file, "[" + original.replace("\"targetText\":\"target\"",
                "\"targetText\":\"other target\"") + "]"), 0, 1);
        assertEquals(examples + 2, count("example_sentences"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void assignmentSetsAreCompleteAndOrderIndependent(boolean file) {
        String original = item(word).replace("[\"N5\"]", "[\"N5\",\"N4\"]")
                .replace("\"lessons\":[", "\"lessons\":[{\"level\":\"N4\",\"lessonNumber\":2,\"displayOrder\":2},");
        counts(submit(file, "[" + original + "]"), 1, 0);
        List<String> before = metadataSnapshot();
        String reordered = original.replace("[\"N5\",\"N4\"]", "[\"N4\",\"N5\"]")
                .replace("{\"level\":\"N4\",\"lessonNumber\":2,\"displayOrder\":2},", "")
                .replace("\"lessonNumber\":1,\"displayOrder\":1}]",
                        "\"lessonNumber\":1,\"displayOrder\":1},{\"level\":\"N4\",\"lessonNumber\":2,\"displayOrder\":2}]");
        counts(submit(file, "[" + reordered + "]"), 0, 1);
        submit(file, "[" + item(word) + "]").statusCode(400);
        submit(file, "[" + original.replace("\"lessonNumber\":2", "\"lessonNumber\":1") + "]")
                .statusCode(400);
        assertEquals(before, metadataSnapshot());
    }

    private ValidatableResponse submit(boolean file, String body) {
        var request = given().auth().oauth2(token);
        return file
                ? request.multiPart("file", "batch.json", body.getBytes(StandardCharsets.UTF_8), "application/json")
                .post("/api/vocabularies/import").then()
                : request.contentType("application/json").body(body).post("/api/vocabularies").then();
    }

    private void counts(ValidatableResponse response, int created, int updated) {
        response.statusCode(200).body("data.total", equalTo(created + updated))
                .body("data.created", equalTo(created)).body("data.updated", equalTo(updated));
    }

    private long count(String table) {
        return pool.query("SELECT COUNT(*) FROM " + table).execute().await().atMost(Duration.ofSeconds(10))
                .iterator().next().getLong(0);
    }

    private List<String> metadataSnapshot() {
        return snapshot(List.of("vocabulary", "vocabulary_levels", "lesson_vocabulary", "vocabulary_readings",
                "vocabulary_meanings", "vocabulary_parts_of_speech", "kanji", "kanji_readings",
                "vocabulary_kanji", "vocabulary_pitch_accents"));
    }

    private List<String> snapshot(List<String> tables) {
        List<String> snapshot = new ArrayList<>();
        for (String table : tables) {
            var rows = pool.query("SELECT * FROM " + table + " ORDER BY 1, 2")
                    .execute().await().atMost(Duration.ofSeconds(10));
            rows.forEach(row -> snapshot.add(table + ":" + row.toJson().encode()));
        }
        return snapshot;
    }

    private String item(String word) {
        return """
                {"word":"%s","normalizedWord":"%s","levels":["N5"],
                 "lessons":[{"level":"N5","lessonNumber":1,"displayOrder":1}],
                 "readings":[{"reading":"reading","isPrimary":true,"displayOrder":1}],
                 "meanings":[{"language":"en","meaning":"meaning","isPrimary":true,"displayOrder":1}],
                 "partsOfSpeech":["NOUN"],
                 "kanji":[{"character":"X","strokeCount":11,"meaningVi":"test","meaningEn":"test",
                    "readings":[{"reading":"test","readingType":"ON","displayOrder":1}]}],
                 "pitchAccents":[{"reading":"reading","accentPattern":0}],
                 "examples":[%s]}
                """.formatted(word, word, example("sentence", "target"));
    }

    private String example(String text, String target) {
        return """
                {"japaneseText":"%s","japaneseReading":"reading","meaningVi":"test",
                 "meaningEn":"test","targetText":"%s","displayOrder":1}
                """.formatted(text, target);
    }
}
