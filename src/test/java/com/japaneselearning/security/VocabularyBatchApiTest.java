package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.response.ValidatableResponse;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.charset.StandardCharsets;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@TestProfile(VocabularyBatchTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class VocabularyBatchApiTest {
    private static final String ENDPOINT = "/api/vocabularies";
    RsaJsonWebKey signingKey;
    private String adminToken;

    @BeforeEach
    void setUp() throws Exception {
        VocabularyBatchTestProfile.reset();
        adminToken = new JwtTestTokens(signingKey).token("Admin");
    }

    @Test
    void adminCanSubmitOneVocabulary() {
        assertCounts(post("[" + item("first") + "]"), 1);
        assertEquals(1, VocabularyBatchTestProfile.vocabularies.size());
    }

    @Test
    void adminCanSubmitMultipleVocabulariesAtConfiguredLimit() {
        assertCounts(post("[" + item("first") + "," + item("second") + "]"), 2);
        assertEquals(2, VocabularyBatchTestProfile.vocabularies.size());
    }

    @Test
    void fileImportUsesSamePipelineAndCounts() {
        assertCounts(upload("[" + item("first") + "," + item("second") + "]"), 2);
        assertEquals(2, VocabularyBatchTestProfile.vocabularies.size());
    }

    @Test
    void rejectsUserAndMissingToken() throws Exception {
        given().contentType("application/json").body("[]").post(ENDPOINT).then().statusCode(401);
        given().auth().oauth2(new JwtTestTokens(signingKey).token("User"))
                .contentType("application/json").body("[]").post(ENDPOINT).then().statusCode(403);
        assertTrue(VocabularyBatchTestProfile.vocabularies.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"[]", "null", "[null]", "[{}]"})
    void rejectsInvalidBatchForBothInputs(String body) {
        assertInvalid(post(body));
        assertInvalid(upload(body));
        assertTrue(VocabularyBatchTestProfile.vocabularies.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{", "{}", "[42]"})
    void rejectsMalformedJson(String body) {
        post(body).statusCode(400).body("success", equalTo(false));
        upload(body).statusCode(400).body("success", equalTo(false));
    }

    @ParameterizedTest
    @ValueSource(strings = {"level", "language", "nullLevel", "nullLanguage", "word", "normalizedWord"})
    void validatesDomainFieldsConsistently(String field) {
        String value = item("first");
        value = switch (field) {
            case "level" -> value.replace("N5", "N6");
            case "language" -> value.replace("\"en\"", "\"fr\"");
            case "nullLevel" -> value.replace("[\"N5\"]", "[null]");
            case "nullLanguage" -> value.replace("\"en\"", "null");
            case "word" -> value.replace("\"word\":\"first\"", "\"word\":\"\"");
            default -> value.replace("\"normalizedWord\":\"first\"", "\"normalizedWord\":null");
        };
        assertInvalid(post("[" + value + "]"));
        assertInvalid(upload("[" + value + "]"));
        assertTrue(VocabularyBatchTestProfile.vocabularies.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"reading\":\"test\",\"readingType\":null}"})
    void rejectsMalformedKanjiReadingsForBothInputs(String reading) {
        String value = item("first").replace("\"partsOfSpeech\":",
                "\"kanji\":[{\"character\":\"test\",\"readings\":[" + reading + "]}],\"partsOfSpeech\":");
        assertInvalid(post("[" + value + "]"));
        assertInvalid(upload("[" + value + "]"));
        assertTrue(VocabularyBatchTestProfile.vocabularies.isEmpty());
    }

    @Test
    void rejectsUnknownPartOfSpeechForBothInputs() {
        String body = "[" + item("first").replace("NOUN", "UNKNOWN") + "]";
        assertInvalid(post(body));
        // Repository doubles do not implement transaction rollback; use a fresh fixture for file parity.
        VocabularyBatchTestProfile.reset();
        assertInvalid(upload(body));
    }

    @Test
    void missingLessonInLaterItemPreventsAllWritesAndNeverCreatesLesson() {
        String body = "[" + item("first") + ","
                + item("second").replace("\"lessonNumber\":1", "\"lessonNumber\":99") + "]";
        assertInvalid(post(body));
        assertInvalid(upload(body));
        assertTrue(VocabularyBatchTestProfile.vocabularies.isEmpty());
    }

    @Test
    void duplicateWordsAreRejectedBeforeWriting() {
        String body = "[" + item("first") + "," + item("first") + "]";
        assertInvalid(post(body));
        assertInvalid(upload(body));
        assertTrue(VocabularyBatchTestProfile.vocabularies.isEmpty());
    }

    @Test
    void existingVocabularyKeepsCoreFields() {
        assertCounts(post("[" + item("first") + "]"), 1);
        Long id = VocabularyBatchTestProfile.vocabularies.get("first").id;
        assertCounts(post("[" + item("first").replace("\"word\":\"first\"", "\"word\":\"variant\"") + "]"), 0, 1);
        assertEquals(1, VocabularyBatchTestProfile.vocabularies.size());
        assertEquals(id, VocabularyBatchTestProfile.vocabularies.get("first").id);
        assertEquals("first", VocabularyBatchTestProfile.vocabularies.get("first").word);
    }

    @Test
    void distinctWordsWithSameNormalizedWordUseCreationThenExamplesOnly() {
        String second = item("second").replace("\"normalizedWord\":\"second\"", "\"normalizedWord\":\"first\"");
        assertCounts(post("[" + item("first") + "," + second + "]"), 1, 1);
        assertEquals(1, VocabularyBatchTestProfile.vocabularies.size());
        assertEquals("first", VocabularyBatchTestProfile.vocabularies.get("first").word);
    }

    @Test
    void enforcesConfiguredLimitForBothInputsBeforeWriting() {
        String body = "[" + item("first") + "," + item("second") + "," + item("third") + "]";
        assertInvalid(post(body));
        assertInvalid(upload(body));
        assertTrue(VocabularyBatchTestProfile.vocabularies.isEmpty());
    }

    @Test
    void openApiDescribesArrayAndWrappedResult() {
        var document = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();
        assertNotNull(document.getMap("components.schemas.ResponseMeta"));
        String operation = "paths.'/api/vocabularies'.post";
        assertEquals("array", document.getString(operation + ".requestBody.content.'application/json'.schema.type"));
        assertEquals("#/components/schemas/VocabularyImportItem", document.getString(
                operation + ".requestBody.content.'application/json'.schema.items.'$ref'"));
        assertEquals("#/components/schemas/ImportResult", document.getString(
                operation + ".responses.'200'.content.'application/json'.schema.properties.data.'$ref'"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void repeatedBatchCreatesNoDuplicateRelationsOrExamples(boolean file) {
        String body = "[" + item("first") + "," + item("second") + "]";
        assertCounts(submit(file, body), 2, 0);
        assertCounts(submit(file, body), 0, 2);
        assertCounts(submit(file, body), 0, 2);
        assertEquals(2, VocabularyBatchTestProfile.creations.size());
        assertEquals(2, VocabularyBatchTestProfile.sentences.size());
        assertEquals(2, VocabularyBatchTestProfile.examples.values().stream().mapToInt(java.util.List::size).sum());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void existingVocabularyAppendsOnlyNewExamples(boolean file) {
        assertCounts(submit(file, "[" + item("first") + "]"), 1);
        var before = VocabularyBatchTestProfile.creations.get(1L);
        var existingExample = VocabularyBatchTestProfile.examples.get(1L).get(0);
        String changed = item("first").replace("\"word\":\"first\"", "\"word\":\"variant\"")
                .replace("\"reading\":\"first\"", "\"reading\":\"changed\"")
                .replace("\"meaning\":\"test\"", "\"meaning\":\"changed\"")
                .replace("\"displayOrder\":1", "\"displayOrder\":9")
                .replace("\"japaneseText\":\"test\"", "\"japaneseText\":\"new example\"");
        assertCounts(submit(file, "[" + changed + "]"), 0, 1);
        assertEquals(1, VocabularyBatchTestProfile.creations.size());
        assertEquals(before, VocabularyBatchTestProfile.creations.get(1L));
        assertEquals("first", VocabularyBatchTestProfile.vocabularies.get("first").word);
        assertEquals(2, VocabularyBatchTestProfile.examples.get(1L).size());
        assertEquals(existingExample, VocabularyBatchTestProfile.examples.get(1L).get(0));
        assertEquals(1, existingExample.displayOrder);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void levelMismatchRejectsBatchBeforeAnyWrites(boolean file) {
        assertCounts(submit(file, "[" + item("existing") + "]"), 1);
        String body = "[" + item("new") + "," + item("existing").replace("N5", "N4") + "]";
        var response = submit(file, body);
        assertInvalid(response);
        response.body("error.message", containsString("existing"))
                .body("error.details.field", hasItem("levels"))
                .body("error.details.message", hasItem("Existing levels: [N5]; requested levels: [N4]"));
        assertEquals(1, VocabularyBatchTestProfile.vocabularies.size());
        assertEquals(1, VocabularyBatchTestProfile.sentences.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void lessonMismatchRejectsWithoutChangingData(boolean file) {
        assertCounts(submit(file, "[" + item("existing") + "]"), 1);
        var response = submit(file, "[" + item("existing")
                .replace("\"lessonNumber\":1", "\"lessonNumber\":2") + "]");
        assertInvalid(response);
        response.body("error.details.field", hasItem("lessons"))
                .body("error.details.message", hasItem("Existing lessons: [N5/1]; requested lessons: [N5/2]"));
        assertEquals(1, VocabularyBatchTestProfile.sentences.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void comparesCompleteAssignmentSetsWithoutDependingOnOrder(boolean file) {
        String multi = item("existing").replace("[\"N5\"]", "[\"N5\",\"N4\"]")
                .replace("\"lessons\":[", "\"lessons\":[{\"level\":\"N4\",\"lessonNumber\":2,\"displayOrder\":2},");
        assertCounts(submit(file, "[" + multi + "]"), 1);
        String reordered = multi.replace("[\"N5\",\"N4\"]", "[\"N4\",\"N5\"]")
                .replace("{\"level\":\"N4\",\"lessonNumber\":2,\"displayOrder\":2},", "")
                .replace("\"lessonNumber\":1,\"displayOrder\":1}]",
                        "\"lessonNumber\":1,\"displayOrder\":1},{\"level\":\"N4\",\"lessonNumber\":2,\"displayOrder\":2}]");
        assertCounts(submit(file, "[" + reordered + "]"), 0, 1);
        assertInvalid(submit(file, "[" + item("existing") + "]"));
        assertInvalid(submit(file, "[" + multi.replace("\"lessonNumber\":2", "\"lessonNumber\":1") + "]"));
        assertEquals(1, VocabularyBatchTestProfile.sentences.size());
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void mixedBatchCountsNewAndExistingAccurately(boolean file) {
        assertCounts(submit(file, "[" + item("existing") + "]"), 1);
        assertCounts(submit(file, "[" + item("new") + "," + item("existing") + "]"), 1, 1);
        assertEquals(2, VocabularyBatchTestProfile.creations.size());
    }

    private ValidatableResponse submit(boolean file, String body) {
        return file ? upload(body) : post(body);
    }

    private ValidatableResponse post(String body) {
        return given().auth().oauth2(adminToken).contentType("application/json")
                .body(body).post(ENDPOINT).then();
    }

    private ValidatableResponse upload(String body) {
        return given().auth().oauth2(adminToken)
                .multiPart("file", "batch.json", body.getBytes(StandardCharsets.UTF_8), "application/json")
                .post(ENDPOINT + "/import").then();
    }

    private void assertInvalid(ValidatableResponse response) {
        response.statusCode(400).body("success", equalTo(false))
                .body("error.code", equalTo("VALIDATION_ERROR"));
    }

    private void assertCounts(ValidatableResponse response, int created) {
        assertCounts(response, created, 0);
    }

    private void assertCounts(ValidatableResponse response, int created, int updated) {
        response.statusCode(200).body("success", equalTo(true))
                .body("data.total", equalTo(created + updated)).body("data.created", equalTo(created))
                .body("data.updated", equalTo(updated));
    }

    private String item(String word) {
        return """
                {"word":"%s","normalizedWord":"%s","levels":["N5"],
                 "lessons":[{"level":"N5","lessonNumber":1,"displayOrder":1}],
                 "readings":[{"reading":"%s","isPrimary":true,"displayOrder":1}],
                 "meanings":[{"language":"en","meaning":"test","isPrimary":true,"displayOrder":1}],
                 "partsOfSpeech":["NOUN"],
                 "examples":[{"japaneseText":"test","japaneseReading":"test","meaningVi":"test",
                              "meaningEn":"test","targetText":"%s","displayOrder":1}]}
                """.formatted(word, word, word, word);
    }
}
