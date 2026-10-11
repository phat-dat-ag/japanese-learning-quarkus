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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(QuizImportMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class QuizImportMysqlTest {
    private static final String BASE = "/api/v1/admin/kanji-quiz/questions";
    private static final String PATH = BASE + "/import";
    private static final String READING = "\u304c\u3063\u3053\u3046";
    private final ObjectMapper mapper = new ObjectMapper();

    @Inject
    MySQLPool pool;

    RsaJsonWebKey signingKey;
    private String token;

    @BeforeEach
    void authenticate() throws Exception {
        token = new JwtTestTokens(signingKey).token("Admin");
    }

    @Test
    void importsMixedSourcesAsOrderedDraftsAtTheConfiguredLimit() throws Exception {
        long exampleId = example();
        Map<String, Object> fromExample = custom();
        content(fromExample).remove("sentenceReading");
        content(fromExample).put("sourceType", "EXAMPLE");
        content(fromExample).put("exampleId", exampleId);
        long before = questionCount();
        var response = upload(List.of(custom(), fromExample, custom())).then().statusCode(200)
                .body("data.total", equalTo(3), "data.imported", equalTo(3), "data.failed", equalTo(0))
                .body("data.results.index", equalTo(List.of(0, 1, 2)))
                .body("data.results.status", equalTo(List.of("IMPORTED", "IMPORTED", "IMPORTED")))
                .extract().jsonPath();
        assertEquals(before + 3, questionCount());
        List<Long> ids = response.getList("data.results.questionId", Long.class);
        for (int index = 0; index < ids.size(); index++) {
            given().auth().oauth2(token).get(BASE + "/" + ids.get(index)).then().statusCode(200)
                    .body("data.status", equalTo("DRAFT"), "data.options.size()", equalTo(4))
                    .body("data.sourceType", equalTo(index == 1 ? "EXAMPLE" : "CUSTOM"));
        }
        assertEquals(1, number("SELECT COUNT(*) FROM example_sentences WHERE id=" + exampleId
                + " AND japanese_reading='" + READING + "'"));
    }

    @Test
    void continuesAfterDomainErrorsAndRetainsEarlierCommits() throws Exception {
        Map<String, Object> invalid = custom();
        content(invalid).put("targetStart", 999);
        long before = questionCount();
        upload(List.of(custom(), invalid, custom())).then().statusCode(200)
                .body("data.imported", equalTo(2), "data.failed", equalTo(1))
                .body("data.results.status", equalTo(List.of("IMPORTED", "FAILED", "IMPORTED")))
                .body("data.results[1].errors[0].field", equalTo("[1].content.targetStart"))
                .body("data.results[1].errors[0].code", equalTo("QUIZ_QUESTION_INVALID"));
        assertEquals(before + 2, questionCount());
    }

    @Test
    void rollsBackPartialQuestionAfterDatabaseConstraintFailureThenContinues() throws Exception {
        Map<String, Object> invalid = custom();
        invalid.put("options", List.of(
                Map.of("text", "one", "correct", true), Map.of("text", "two", "correct", false),
                Map.of("text", "reject-import-item", "correct", false), Map.of("text", "four", "correct", false)
        ));
        long before = questionCount();
        long options = number("SELECT COUNT(*) FROM quiz_question_options");
        // Disposable test schema only: force failure after the question and earlier options were inserted.
        sql("ALTER TABLE quiz_question_options ADD CONSTRAINT ck_import_test_option "
                + "CHECK (option_text <> 'reject-import-item')");
        try {
            upload(List.of(custom(), invalid, custom())).then().statusCode(200)
                    .body("data.results.status", equalTo(List.of("IMPORTED", "FAILED", "IMPORTED")))
                    .body("data.results[1].errors[0].code", equalTo("QUIZ_QUESTION_CONFLICT"));
            assertEquals(before + 2, questionCount());
            assertEquals(options + 8, number("SELECT COUNT(*) FROM quiz_question_options"));
            assertEquals(0, number("SELECT COUNT(*) FROM quiz_questions WHERE sentence_reading='"
                    + content(invalid).get("sentenceReading") + "'"));
        } finally {
            sql("ALTER TABLE quiz_question_options DROP CHECK ck_import_test_option");
        }
    }

    @Test
    void returnsAllItemFailuresAndMultipleBeanValidationErrors() throws Exception {
        Map<String, Object> invalid = custom();
        content(invalid).remove("targetStart");
        content(invalid).remove("targetLength");
        invalid.put("options", List.of(Map.of("text", " ", "correct", true)));
        Map<String, Object> missing = custom();
        content(missing).put("vocabularyId", Long.MAX_VALUE);
        long before = questionCount();
        var result = upload(Arrays.asList(invalid, missing, null)).then().statusCode(200)
                .body("data.total", equalTo(3), "data.imported", equalTo(0), "data.failed", equalTo(3))
                .body("data.results.index", equalTo(List.of(0, 1, 2)))
                .body("data.results[0].errors.field", hasItem("[0].content.targetStart"))
                .body("data.results[0].errors.field", hasItem("[0].content.targetLength"))
                .body("data.results[0].errors.field", hasItem("[0].options[0].text"))
                .body("data.results[1].errors[0].code", equalTo("QUIZ_REFERENCE_NOT_FOUND"))
                .body("data.results[2].errors[0].field", equalTo("[2]"))
                .extract().jsonPath();
        for (Map<String, Object> item : result.<Map<String, Object>>getList("data.results")) {
            assertTrue(!item.containsKey("questionId") && !item.containsKey("version"));
        }
        assertEquals(before, questionCount());
    }

    @Test
    void rejectsSourceOwnershipAndOptionRulesPerItem() throws Exception {
        Map<String, Object> source = custom();
        content(source).put("sourceType", "EXAMPLE");
        content(source).put("exampleId", example());
        Map<String, Object> options = custom();
        options.put("options", List.of(
                Map.of("text", "one", "correct", false), Map.of("text", "two", "correct", false),
                Map.of("text", "three", "correct", false), Map.of("text", "four", "correct", false)
        ));
        upload(List.of(source, options, custom())).then().statusCode(200)
                .body("data.results.status", equalTo(List.of("FAILED", "FAILED", "IMPORTED")))
                .body("data.results[0].errors[0].field", equalTo("[0].content.sourceType"))
                .body("data.results[1].errors[0].field", equalTo("[1].options"));
    }

    @Test
    void duplicateItemsFailIndividuallyAndDoNotBlockLaterItems() throws Exception {
        Map<String, Object> duplicate = custom();
        long before = questionCount();
        upload(List.of(duplicate, duplicate, custom())).then().statusCode(200)
                .body("data.results.status", equalTo(List.of("IMPORTED", "FAILED", "IMPORTED")))
                .body("data.results[1].errors[0].code", equalTo("QUIZ_QUESTION_CONFLICT"));
        assertEquals(before + 2, questionCount());
        upload(List.of(duplicate, custom())).then().statusCode(200)
                .body("data.results.status", equalTo(List.of("FAILED", "IMPORTED")));
        assertEquals(before + 3, questionCount());
    }

    @Test
    void concurrentImportsNeverCreateDuplicateQuestions() throws Exception {
        byte[] batch = mapper.writeValueAsBytes(List.of(custom(), custom()));
        long before = questionCount();
        var first = CompletableFuture.supplyAsync(() -> uploadBytes(batch, "application/json"));
        var second = CompletableFuture.supplyAsync(() -> uploadBytes(batch, "application/json"));
        List<Response> responses = List.of(first.get(30, TimeUnit.SECONDS), second.get(30, TimeUnit.SECONDS));
        int imported = 0;
        int failed = 0;
        for (Response response : responses) {
            response.then().statusCode(200).body("data.results.index", equalTo(List.of(0, 1)));
            imported += response.jsonPath().getInt("data.imported");
            failed += response.jsonPath().getInt("data.failed");
        }
        assertEquals(2, imported);
        assertEquals(2, failed);
        assertEquals(before + 2, questionCount());
    }

    @Test
    void concurrentImportAndSingleCreateShareDuplicateProtection() throws Exception {
        Map<String, Object> item = custom();
        byte[] batch = mapper.writeValueAsBytes(List.of(custom(), item));
        long before = questionCount();
        var importing = CompletableFuture.supplyAsync(() -> uploadBytes(batch, "application/json"));
        var creating = CompletableFuture.supplyAsync(() -> given().auth().oauth2(token)
                .contentType("application/json").body(item).post(BASE).statusCode());
        Response response = importing.get(30, TimeUnit.SECONDS);
        response.then().statusCode(200);
        int created = creating.get(30, TimeUnit.SECONDS);
        assertTrue(created == 200 || created == 409);
        assertEquals(created == 200 ? 1 : 2, response.jsonPath().getInt("data.imported"));
        assertEquals(before + 2, questionCount());
    }

    @Test
    void malformedTailAndRequestLevelLimitsNeverProcessEarlierValidItems() throws Exception {
        byte[] valid = mapper.writeValueAsBytes(List.of(custom()));
        String prefix = new String(valid, StandardCharsets.UTF_8);
        long before = questionCount();
        for (String body : List.of("", " ", "null", "[]", "{}", "[", prefix + " {}",
                prefix.substring(0, prefix.length() - 1) + ",")) {
            uploadBytes(body.getBytes(StandardCharsets.UTF_8), "application/json").then().statusCode(400);
        }
        upload(List.of(custom(), custom(), custom(), custom())).then().statusCode(400);
        uploadBytes(new byte[4097], "application/json").then().statusCode(413)
                .body("error.code", equalTo("PAYLOAD_TOO_LARGE"));
        uploadBytes(valid, "text/csv").then().statusCode(400);
        assertEquals(before, questionCount());
    }

    @Test
    void wrongTypesUnknownFieldsAndNonObjectsBecomeItemFailures() throws Exception {
        Map<String, Object> wrongType = custom();
        content(wrongType).put("targetStart", "0");
        Map<String, Object> unknown = custom();
        content(unknown).put("unsupported", true);
        upload(List.of(wrongType, unknown, custom())).then().statusCode(200)
                .body("data.results.status", equalTo(List.of("FAILED", "FAILED", "IMPORTED")))
                .body("data.results[0].errors[0].field", equalTo("[0].content.targetStart"))
                .body("data.results[0].errors[0].code", equalTo("QUIZ_ITEM_JSON_INVALID"))
                .body("data.results[1].errors[0].field", equalTo("[1].content.unsupported"));
        uploadBytes("[null,1,true]".getBytes(StandardCharsets.UTF_8), "application/json").then().statusCode(200)
                .body("data.imported", equalTo(0), "data.failed", equalTo(3));
    }

    @Test
    void acceptsExactFileLimitAndDistinctExampleTargets() throws Exception {
        long exampleId = example();
        Map<String, Object> first = custom();
        content(first).remove("sentenceReading");
        content(first).put("sourceType", "EXAMPLE");
        content(first).put("exampleId", exampleId);
        Map<String, Object> second = custom();
        content(second).clear();
        content(second).putAll(content(first));
        content(second).put("targetStart", 2);
        content(second).put("targetLength", 2);
        content(second).put("targetReading", READING.substring(2));
        byte[] json = mapper.writeValueAsBytes(List.of(first, second));
        byte[] padded = Arrays.copyOf(json, 4096);
        Arrays.fill(padded, json.length, padded.length, (byte) ' ');
        uploadBytes(padded, "application/octet-stream").then().statusCode(200)
                .body("data.imported", equalTo(2), "data.failed", equalTo(0));
    }

    @Test
    void requiresAdminAndMultipartFile() throws Exception {
        given().multiPart("file", "quiz.json", "[]".getBytes(StandardCharsets.UTF_8), "application/json")
                .post(PATH).then().statusCode(401);
        for (String role : List.of("User", "admin")) {
            given().auth().oauth2(new JwtTestTokens(signingKey).token(role))
                    .multiPart("file", "quiz.json", "[]".getBytes(StandardCharsets.UTF_8), "application/json")
                    .post(PATH).then().statusCode(403);
        }
        given().auth().oauth2(token).multiPart("other", "ignored").post(PATH).then().statusCode(400);
        given().auth().oauth2(token).contentType("application/json").body("[]").post(PATH)
                .then().statusCode(415);
    }

    private Map<String, Object> custom() {
        Map<String, Object> content = new HashMap<>();
        content.put("sourceType", "CUSTOM");
        content.put("sentenceReading", READING + UUID.randomUUID());
        content.put("targetStart", 0);
        content.put("targetLength", 4);
        content.put("targetReading", READING);
        content.put("levelIds", List.of());
        content.put("lessonIds", List.of());
        List<Map<String, Object>> options = new ArrayList<>();
        for (int index = 0; index < 4; index++) {
            options.add(Map.of("text", "choice-" + index, "correct", index == 0));
        }
        return new HashMap<>(Map.of("content", content, "options", options));
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> content(Map<String, Object> item) {
        return (Map<String, Object>) item.get("content");
    }

    private long example() {
        String marker = UUID.randomUUID().toString();
        sql("INSERT INTO example_sentences(japanese_text,japanese_reading,meaning_vi,meaning_en) VALUES ('"
                + marker + "','" + READING + "','vi','en')");
        return number("SELECT id FROM example_sentences WHERE japanese_text='" + marker + "'");
    }

    private Response upload(Object items) throws Exception {
        return uploadBytes(mapper.writeValueAsBytes(items), "application/json");
    }

    private Response uploadBytes(byte[] bytes, String contentType) {
        return given().auth().oauth2(token).multiPart("file", "quiz.json", bytes, contentType).post(PATH);
    }

    private long questionCount() {
        return number("SELECT COUNT(*) FROM quiz_questions");
    }

    private void sql(String query) {
        pool.query(query).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long number(String query) {
        return pool.query(query).execute().await().atMost(Duration.ofSeconds(10)).iterator().next().getLong(0);
    }
}
