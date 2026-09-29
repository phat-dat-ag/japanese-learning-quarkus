package com.japaneselearning.security;

import static io.restassured.RestAssured.given;

import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;

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
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

@QuarkusTest
@TestProfile(VocabularyMysqlTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
@EnabledIfSystemProperty(named = "vocabulary.mysql.tests", matches = "true")
class AdminVocabularyEditMysqlTest {
    @Inject
    MySQLPool pool;
    RsaJsonWebKey signingKey;
    private String token;
    private String fixtureSuffix;
    private long vocabularyId;
    private long otherVocabularyId;
    private long readingId;
    private long kanjiId;

    @BeforeEach
    void setup() throws Exception {
        token = new JwtTestTokens(signingKey).token("Admin");
        fixtureSuffix = UUID.randomUUID().toString().replace("-", "");
        vocabularyId = seedVocabulary("edit-" + fixtureSuffix);
        otherVocabularyId = seedVocabulary("other-" + fixtureSuffix);
        executeSql(
                "INSERT INTO vocabulary_readings(vocabulary_id,reading,is_primary,display_order)"
                        + " VALUES ("
                        + vocabularyId
                        + ",'initial',b'1',0)");
        readingId =
                queryLong("SELECT id FROM vocabulary_readings WHERE vocabulary_id=" + vocabularyId);
        executeSql(
                "INSERT INTO vocabulary_levels(vocabulary_id,level_id,display_order) SELECT "
                        + vocabularyId
                        + ",id,0 FROM jlpt_levels WHERE code='N5'");
        for (int itemNumber = 1; itemNumber <= 3; itemNumber++) {
            executeSql(
                    "INSERT INTO lessons(level_id,lesson_number,title,display_order) SELECT id,"
                            + itemNumber
                            + ",'Edit fixture',"
                            + itemNumber
                            + " FROM jlpt_levels WHERE code='N5' ON DUPLICATE KEY UPDATE"
                            + " title=title");
        }
    }

    @Test
    void schemaValidationLeavesReactiveTransactionsUsable() {
        List<String> validationErrors = new ArrayList<>();
        Logger schemaLogger =
                Logger.getLogger(
                        "io.quarkus.hibernate.orm.runtime.schema.SchemaManagementIntegrator");
        Handler handler =
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        if (record.getLevel().intValue() >= Level.SEVERE.intValue()) {
                            validationErrors.add(record.getMessage());
                        }
                    }

                    @Override
                    public void flush() {
                    }

                    @Override
                    public void close() {
                    }
                };
        schemaLogger.addHandler(handler);
        try {
            // The test service registration enables the same integrator used in development mode.
            io.quarkus.hibernate.orm.runtime.schema.SchemaManagementIntegrator
                    .runPostBootValidation("<default>");
        } finally {
            schemaLogger.removeHandler(handler);
        }
        assertEquals(List.of(), validationErrors, "Flyway schema must pass post-boot validation");
        assertEquals(
                "smallint unsigned",
                queryText(
                        "SELECT COLUMN_TYPE FROM information_schema.columns WHERE"
                                + " table_schema=DATABASE() AND table_name='kanji' AND"
                                + " column_name='stroke_count'"));
        assertEquals(
                "smallint unsigned",
                queryText(
                        "SELECT COLUMN_TYPE FROM information_schema.columns WHERE"
                                + " table_schema=DATABASE() AND table_name='vocabulary_pitch_accents'"
                                + " AND column_name='accent_pattern'"));

        sendAdminRequest(
                "POST",
                vocabularyId,
                "/meanings",
                List.of(requestForSection("meanings", 1)))
                .statusCode(200);
        assertEquals(1, countSectionRows("meanings"));
    }

    @Test
    void coreUpdatesOnlyAnExistingVocabularyAndPreservesUniqueness() {
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "",
                Map.of("word", "edited", "normalizedWord", "changed-" + fixtureSuffix))
                .statusCode(200)
                .body("data.vocabularyId", equalTo((int) vocabularyId));
        assertEquals("edited", queryText("SELECT word FROM vocabulary WHERE id=" + vocabularyId));
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "",
                Map.of("word", "bad", "normalizedWord", "other-" + fixtureSuffix))
                .statusCode(409);
        assertEquals("edited", queryText("SELECT word FROM vocabulary WHERE id=" + vocabularyId));
        sendAdminRequest(
                "PUT",
                Long.MAX_VALUE,
                "",
                Map.of("word", "edited", "normalizedWord", "unused"))
                .statusCode(404);
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "readings",
                    "meanings",
                    "examples",
                    "pitch-accents",
                    "levels",
                    "lessons",
                    "parts-of-speech",
                    "kanji",
                    "kanji-readings"
            })
    void addsOneAndMultipleItemsAndRejectsDuplicatesAtomically(String section) {
        String path = sectionPath(section);
        long initialCount = countSectionRows(section);
        sendAdminRequest("POST", vocabularyId, path, List.of(requestForSection(section, 1)))
                .statusCode(200)
                .body("data[0]." + resultIdField(section), notNullValue());
        sendAdminRequest(
                "POST",
                vocabularyId,
                path,
                List.of(requestForSection(section, 2), requestForSection(section, 3)))
                .statusCode(200);
        assertEquals(initialCount + 3, countSectionRows(section));
        sendAdminRequest("POST", vocabularyId, path, List.of(requestForSection(section, 1)))
                .statusCode(409);
        assertEquals(initialCount + 3, countSectionRows(section));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "readings",
                    "meanings",
                    "examples",
                    "pitch-accents",
                    "levels",
                    "lessons",
                    "parts-of-speech",
                    "kanji",
                    "kanji-readings"
            })
    void duplicateInputAndLaterDatabaseConflictRollBackTheEntireAdd(String section) {
        String path = sectionPath(section);
        long initialCount = countSectionRows(section);
        sendAdminRequest(
                "POST",
                vocabularyId,
                path,
                List.of(requestForSection(section, 1), requestForSection(section, 1)))
                .statusCode(409);
        assertEquals(initialCount, countSectionRows(section));
        sendAdminRequest("POST", vocabularyId, path, List.of(requestForSection(section, 1)))
                .statusCode(200)
                .body("data[0]." + resultIdField(section), notNullValue());
        sendAdminRequest(
                "POST",
                vocabularyId,
                path,
                List.of(requestForSection(section, 2), requestForSection(section, 1)))
                .statusCode(409);
        assertEquals(initialCount + 1, countSectionRows(section));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "readings",
                    "meanings",
                    "examples",
                    "pitch-accents",
                    "levels",
                    "lessons",
                    "parts-of-speech",
                    "kanji",
                    "kanji-readings"
            })
    void malformedMiddleItemAndUnknownVocabularyMakeNoChanges(String section) {
        String path = sectionPath(section);
        long initialCount = countSectionRows(section);
        sendAdminRequest(
                "POST",
                vocabularyId,
                path,
                List.of(
                        requestForSection(section, 1),
                        Map.of(),
                        requestForSection(section, 2)))
                .statusCode(400);
        sendAdminRequest("POST", vocabularyId, path, List.of()).statusCode(400);
        sendAdminRequest("POST", Long.MAX_VALUE, path, List.of(requestForSection(section, 1)))
                .statusCode(404);
        assertEquals(initialCount, countSectionRows(section));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "readings",
                    "meanings",
                    "examples",
                    "pitch-accents",
                    "levels",
                    "lessons",
                    "kanji",
                    "kanji-readings"
            })
    void updateVerifiesVocabularyAndEveryParentAndChild(String section) {
        String path = sectionPath(section);
        long editedResourceId =
                sendAdminRequest("POST", vocabularyId, path, List.of(requestForSection(section, 1)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0]." + resultIdField(section));
        Map<String, Object> update =
                section.equals("levels") || section.equals("lessons")
                        ? Map.of("displayOrder", 9)
                        : requestForSection(section, 2);
        sendAdminRequest("PUT", vocabularyId, path + "/" + editedResourceId, update)
                .statusCode(200);
        String expected =
                switch (section) {
                    case "levels", "lessons" -> "9";
                    case "readings", "kanji-readings" -> "reading-2";
                    case "meanings" -> "meaning-2";
                    case "examples" -> "sentence-2";
                    case "pitch-accents" -> "2";
                    case "kanji" -> fixtureSuffix.substring(0, 7) + "2";
                    default -> throw new IllegalArgumentException(section);
                };
        assertEquals(expected, queryEditedField(section, editedResourceId));
        sendAdminRequest("PUT", vocabularyId, path + "/" + Long.MAX_VALUE, update).statusCode(404);
        sendAdminRequest("PUT", otherVocabularyId, path + "/" + editedResourceId, update)
                .statusCode(404);
        sendAdminRequest("PUT", Long.MAX_VALUE, path + "/" + editedResourceId, update)
                .statusCode(404);
        sendAdminRequest("PUT", vocabularyId, path + "/" + editedResourceId, Map.of())
                .statusCode(400);
        assertEquals(expected, queryEditedField(section, editedResourceId));
        assertEquals(
                1 + (section.equals("readings") || section.equals("levels") ? 1 : 0),
                countSectionRows(section));
    }

    @Test
    void masterDataMustExistAndLessonLevelMustBeAssigned() {
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/levels",
                List.of(Map.of("level", "N9", "displayOrder", 0)))
                .statusCode(404);
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/parts-of-speech",
                List.of(Map.of("code", "UNKNOWN")))
                .statusCode(404);
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/lessons",
                List.of(Map.of("lessonId", Long.MAX_VALUE, "displayOrder", 1)))
                .statusCode(404);
        long lesson =
                queryLong(
                        "SELECT l.id FROM lessons l JOIN jlpt_levels j ON j.id=l.level_id WHERE"
                                + " j.code='N5' AND lesson_number=1");
        sendAdminRequest(
                "POST",
                otherVocabularyId,
                "/lessons",
                List.of(Map.of("lessonId", lesson, "displayOrder", 1)))
                .statusCode(400);
        assertEquals(
                0,
                queryLong(
                        "SELECT COUNT(*) FROM lesson_vocabulary WHERE vocabulary_id="
                                + otherVocabularyId));
    }

    @Test
    void readingMustRetainAPrimaryButMultiplePrimaryReadingsAreAllowed() {
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/readings/" + readingId,
                Map.of("reading", "initial", "isPrimary", false, "displayOrder", 0))
                .statusCode(400);
        Map<String, Object> primary = new HashMap<>(requestForSection("readings", 1));
        primary.put("isPrimary", true);
        sendAdminRequest("POST", vocabularyId, "/readings", List.of(primary)).statusCode(200);
        assertEquals(
                2,
                queryLong(
                        "SELECT COUNT(*) FROM vocabulary_readings WHERE is_primary=1 AND"
                                + " vocabulary_id="
                                + vocabularyId));
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/readings/" + readingId,
                Map.of("reading", "initial", "isPrimary", false, "displayOrder", 0))
                .statusCode(200);
    }

    @Test
    void meaningsPreserveExistingLanguageAndPrimarySemantics() {
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/meanings",
                List.of(requestForSection("meanings", 1), requestForSection("meanings", 2)))
                .statusCode(200);
        assertEquals(
                2,
                queryLong(
                        "SELECT COUNT(*) FROM vocabulary_meanings WHERE is_primary=1 AND"
                                + " vocabulary_id="
                                + vocabularyId));
        Map<String, Object> invalid = new HashMap<>(requestForSection("meanings", 3));
        invalid.put("language", "fr");
        sendAdminRequest("POST", vocabularyId, "/meanings", List.of(invalid)).statusCode(400);
    }

    @Test
    void pitchAccentsCannotUseAnotherVocabularyReading() {
        executeSql(
                "INSERT INTO vocabulary_readings(vocabulary_id,reading,is_primary,display_order)"
                        + " VALUES ("
                        + otherVocabularyId
                        + ",'foreign',b'1',0)");
        long foreignReadingId =
                queryLong(
                        "SELECT id FROM vocabulary_readings WHERE vocabulary_id="
                                + otherVocabularyId);
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/pitch-accents",
                List.of(Map.of("readingId", foreignReadingId, "accentPattern", 0)))
                .statusCode(404);
        long editedResourceId =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        "/pitch-accents",
                        List.of(requestForSection("pitch-accents", 1)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].pitchAccentId");
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/pitch-accents/" + editedResourceId,
                Map.of("readingId", foreignReadingId, "accentPattern", 0))
                .statusCode(404);
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/pitch-accents/" + editedResourceId,
                Map.of("readingId", readingId, "accentPattern", -1))
                .statusCode(400);
        assertEquals(
                readingId,
                queryLong(
                        "SELECT vocabulary_reading_id FROM vocabulary_pitch_accents WHERE id="
                                + editedResourceId));
    }

    @Test
    void sharedKanjiMetadataAndReadingsAreProtectedButOrderingIsLocal() {
        Map<String, Object> original = requestForSection("kanji", 1);
        long editedResourceId =
                sendAdminRequest("POST", vocabularyId, "/kanji", List.of(original))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].kanjiId");
        long kanjiReadingId =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        "/kanji/" + editedResourceId + "/readings",
                        List.of(requestForSection("kanji-readings", 1)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].kanjiReadingId");
        sendAdminRequest("POST", otherVocabularyId, "/kanji", List.of(original)).statusCode(200);
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/kanji/" + editedResourceId + "/readings/" + kanjiReadingId,
                requestForSection("kanji-readings", 2))
                .statusCode(409);
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/kanji/" + editedResourceId,
                requestForSection("kanji", 2))
                .statusCode(409);
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/kanji/" + editedResourceId + "/readings",
                List.of(requestForSection("kanji-readings", 1)))
                .statusCode(409);
        Map<String, Object> orderOnly = new HashMap<>(original);
        orderOnly.put("displayOrder", 9);
        sendAdminRequest("PUT", vocabularyId, "/kanji/" + editedResourceId, orderOnly)
                .statusCode(200);
        assertEquals(
                1,
                queryLong(
                        "SELECT display_order FROM vocabulary_kanji WHERE kanji_id="
                                + editedResourceId
                                + " AND vocabulary_id="
                                + otherVocabularyId));
        assertEquals(
                original.get("character"),
                queryText("SELECT kanji_character FROM kanji WHERE id=" + editedResourceId));
    }

    @Test
    void kanjiReadingMustBelongToTheSpecifiedKanji() {
        String path = sectionPath("kanji-readings");
        long readingId =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        path,
                        List.of(requestForSection("kanji-readings", 1)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].kanjiReadingId");
        long otherKanjiId =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        "/kanji",
                        List.of(requestForSection("kanji", 4)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].kanjiId");
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/kanji/" + otherKanjiId + "/readings/" + readingId,
                requestForSection("kanji-readings", 2))
                .statusCode(404);
        sendAdminRequest(
                "POST",
                otherVocabularyId,
                path,
                List.of(requestForSection("kanji-readings", 2)))
                .statusCode(404);
    }

    @Test
    void sharedExamplesProtectSentenceContentButAllowLocalAssociationEdits() {
        Map<String, Object> original = requestForSection("examples", 1);
        long editedResourceId =
                sendAdminRequest("POST", vocabularyId, "/examples", List.of(original))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].exampleId");
        executeSql(
                "INSERT INTO"
                        + " vocabulary_examples(vocabulary_id,example_sentence_id,target_text,display_order)"
                        + " VALUES ("
                        + otherVocabularyId
                        + ","
                        + editedResourceId
                        + ",'other target',1)");
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/examples/" + editedResourceId,
                requestForSection("examples", 2))
                .statusCode(409);
        Map<String, Object> orderOnly = new HashMap<>(original);
        orderOnly.put("displayOrder", 9);
        sendAdminRequest("PUT", vocabularyId, "/examples/" + editedResourceId, orderOnly)
                .statusCode(200);
        assertEquals(
                original.get("japaneseText"),
                queryText(
                        "SELECT japanese_text FROM example_sentences WHERE id="
                                + editedResourceId));
        assertEquals(
                1,
                queryLong(
                        "SELECT display_order FROM vocabulary_examples WHERE example_sentence_id="
                                + editedResourceId
                                + " AND vocabulary_id="
                                + otherVocabularyId));
    }

    @Test
    void examplesUseImportIdentityAndRejectConflictingUpdates() {
        long first =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        "/examples",
                        List.of(requestForSection("examples", 1)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].exampleId");
        Map<String, Object> duplicate = new HashMap<>(requestForSection("examples", 1));
        duplicate.put("meaningEn", "different translation");
        duplicate.put("displayOrder", 9);
        sendAdminRequest("POST", vocabularyId, "/examples", List.of(duplicate)).statusCode(409);
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/examples",
                List.of(requestForSection("examples", 2)))
                .statusCode(200);
        sendAdminRequest(
                "PUT", vocabularyId, "/examples/" + first, requestForSection("examples", 2))
                .statusCode(409);
        assertEquals(2, countSectionRows("examples"));
    }

    @Test
    void concurrentExampleAddsAreSerializedWithinTheEditApis() {
        Object body = List.of(requestForSection("examples", 1));
        var first =
                CompletableFuture.supplyAsync(
                        () ->
                                sendAdminRequest("POST", vocabularyId, "/examples", body)
                                        .extract()
                                        .statusCode());
        var second =
                CompletableFuture.supplyAsync(
                        () ->
                                sendAdminRequest("POST", vocabularyId, "/examples", body)
                                        .extract()
                                        .statusCode());

        assertEquals(Set.of(200, 409), Set.of(first.join(), second.join()));
        assertEquals(1, countSectionRows("examples"));
    }

    @Test
    void concurrentCoreUniquenessConflictIsSafe() {
        Object body = Map.of("word", "same", "normalizedWord", "concurrent-" + fixtureSuffix);
        var first =
                CompletableFuture.supplyAsync(
                        () ->
                                sendAdminRequest("PUT", vocabularyId, "", body)
                                        .extract()
                                        .statusCode());
        var second =
                CompletableFuture.supplyAsync(
                        () ->
                                sendAdminRequest("PUT", otherVocabularyId, "", body)
                                        .extract()
                                        .statusCode());

        assertEquals(Set.of(200, 409), Set.of(first.join(), second.join()));
    }

    @Test
    void addBatchLimitIsEnforcedBeforeWriting() {
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/examples",
                java.util.Collections.nCopies(101, requestForSection("examples", 1)))
                .statusCode(400);
        assertEquals(0, countSectionRows("examples"));
    }

    @ParameterizedTest
    @CsvSource({
            "kanji,0",
            "kanji,32768",
            "kanji,65535",
            "pitch-accents,0",
            "pitch-accents,32768",
            "pitch-accents,65535"
    })
    void unsignedSmallintValuesRoundTripWithoutNarrowing(String section, int value) {
        Map<String, Object> request = new HashMap<>(requestForSection(section, 1));
        String field = section.equals("kanji") ? "strokeCount" : "accentPattern";
        request.put(field, value);
        long resourceId =
                sendAdminRequest("POST", vocabularyId, "/" + section, List.of(request))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0]." + resultIdField(section));
        sendAdminRequest("PUT", vocabularyId, "/" + section + "/" + resourceId, request)
                .statusCode(200)
                .body("data." + resultIdField(section), equalTo((int) resourceId));
        String query =
                section.equals("kanji")
                        ? "SELECT stroke_count FROM kanji WHERE id="
                        : "SELECT accent_pattern FROM vocabulary_pitch_accents WHERE id=";
        assertEquals(value, queryLong(query + resourceId));
        request.put(field, 65536);
        sendAdminRequest("PUT", vocabularyId, "/" + section + "/" + resourceId, request)
                .statusCode(400);
        assertEquals(value, queryLong(query + resourceId));
    }

    @Test
    void nullableKanjiMetadataCanBeClearedWithoutChangingItsReadings() {
        long assignedKanjiId =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        "/kanji",
                        List.of(requestForSection("kanji", 1)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].kanjiId");
        long kanjiReadingId =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        "/kanji/" + assignedKanjiId + "/readings",
                        List.of(requestForSection("kanji-readings", 1)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].kanjiReadingId");
        sendAdminRequest(
                "PUT",
                vocabularyId,
                "/kanji/" + assignedKanjiId,
                Map.of(
                        "character",
                        requestForSection("kanji", 1).get("character"),
                        "displayOrder",
                        4))
                .statusCode(200);
        assertEquals(
                1,
                queryLong(
                        "SELECT COUNT(*) FROM kanji WHERE id="
                                + assignedKanjiId
                                + " AND stroke_count IS NULL AND meaning_vi IS NULL AND meaning_en"
                                + " IS NULL"));
        assertEquals(
                "reading-1",
                queryText("SELECT reading FROM kanji_readings WHERE id=" + kanjiReadingId));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                    "readings",
                    "meanings",
                    "examples",
                    "pitch-accents",
                    "levels",
                    "lessons",
                    "kanji",
                    "kanji-readings"
            })
    void updatesOneChildWithoutChangingItsSiblingOrVocabularyCore(String section) {
        String path = sectionPath(section);
        List<Map<String, Object>> results =
                sendAdminRequest(
                        "POST",
                        vocabularyId,
                        path,
                        List.of(
                                requestForSection(section, 1),
                                requestForSection(section, 2)))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getList("data");
        long firstId = ((Number) results.get(0).get(resultIdField(section))).longValue();
        long siblingId = ((Number) results.get(1).get(resultIdField(section))).longValue();
        String siblingValue = queryEditedField(section, siblingId);
        Map<String, Object> update =
                section.equals("levels") || section.equals("lessons")
                        ? Map.of("displayOrder", 9)
                        : requestForSection(section, 3);
        sendAdminRequest("PUT", vocabularyId, path + "/" + firstId, update).statusCode(200);
        assertEquals(siblingValue, queryEditedField(section, siblingId));
        assertEquals(
                "edit-" + fixtureSuffix,
                queryText("SELECT word FROM vocabulary WHERE id=" + vocabularyId));
        assertEquals(
                1, queryLong("SELECT is_primary FROM vocabulary_readings WHERE id=" + readingId));
    }

    @ParameterizedTest
    @ValueSource(strings = {"levels", "lessons", "parts-of-speech", "pitch-accents"})
    void missingReferenceAfterAValidItemRollsBackTheCollection(String section) {
        Map<String, Object> missingReference =
                switch (section) {
                    case "levels" -> Map.of("level", "N9", "displayOrder", 0);
                    case "lessons" -> Map.of("lessonId", Long.MAX_VALUE, "displayOrder", 1);
                    case "parts-of-speech" -> Map.of("code", "UNKNOWN");
                    case "pitch-accents" -> Map.of("readingId", Long.MAX_VALUE, "accentPattern", 0);
                    default -> throw new IllegalArgumentException(section);
                };
        long initialCount = countSectionRows(section);
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/" + section,
                List.of(requestForSection(section, 1), missingReference))
                .statusCode(404)
                .body(
                        "success",
                        equalTo(false),
                        "error.code",
                        equalTo("RESOURCE_NOT_FOUND"),
                        "meta.traceId",
                        notNullValue(),
                        "meta.correlationId",
                        notNullValue());
        assertEquals(initialCount, countSectionRows(section));
    }

    @Test
    void attachingExistingKanjiNeverOverwritesMetadataAndRollsBackEarlierItems() {
        Map<String, Object> existing = requestForSection("kanji", 1);
        long sharedKanjiId =
                sendAdminRequest("POST", otherVocabularyId, "/kanji", List.of(existing))
                        .statusCode(200)
                        .extract()
                        .jsonPath()
                        .getLong("data[0].kanjiId");
        Map<String, Object> conflicting = new HashMap<>(existing);
        conflicting.put("meaningEn", "replacement");
        sendAdminRequest(
                "POST",
                vocabularyId,
                "/kanji",
                List.of(requestForSection("kanji", 2), conflicting))
                .statusCode(409)
                .body(
                        "success",
                        equalTo(false),
                        "error.code",
                        equalTo("VOCABULARY_EDIT_CONFLICT"),
                        "meta.traceId",
                        notNullValue(),
                        "meta.correlationId",
                        notNullValue());
        assertEquals(0, countSectionRows("kanji"));
        assertEquals("test", queryText("SELECT meaning_en FROM kanji WHERE id=" + sharedKanjiId));
        assertEquals(
                0,
                queryLong(
                        "SELECT COUNT(*) FROM kanji WHERE kanji_character='"
                                + requestForSection("kanji", 2).get("character")
                                + "'"));
    }

    private String resultIdField(String section) {
        return switch (section) {
            case "readings" -> "readingId";
            case "meanings" -> "meaningId";
            case "examples" -> "exampleId";
            case "pitch-accents" -> "pitchAccentId";
            case "levels" -> "levelId";
            case "lessons" -> "lessonId";
            case "parts-of-speech" -> "partOfSpeechId";
            case "kanji" -> "kanjiId";
            case "kanji-readings" -> "kanjiReadingId";
            default -> throw new IllegalArgumentException(section);
        };
    }

    private String sectionPath(String section) {
        if (!section.equals("kanji-readings")) {
            return "/" + section;
        }
        if (kanjiId == 0) {
            kanjiId =
                    sendAdminRequest(
                            "POST",
                            vocabularyId,
                            "/kanji",
                            List.of(requestForSection("kanji", 0)))
                            .statusCode(200)
                            .extract()
                            .jsonPath()
                            .getLong("data[0].kanjiId");
        }
        return "/kanji/" + kanjiId + "/readings";
    }

    private Map<String, Object> requestForSection(String section, int itemNumber) {
        return switch (section) {
            case "readings" -> Map.of(
                    "reading",
                    "reading-" + itemNumber,
                    "isPrimary",
                    false,
                    "displayOrder",
                    itemNumber);
            case "meanings" -> Map.of(
                    "language",
                    "en",
                    "meaning",
                    "meaning-" + itemNumber,
                    "isPrimary",
                    true,
                    "displayOrder",
                    itemNumber);
            case "examples" -> Map.of(
                    "japaneseText", "sentence-" + itemNumber,
                    "japaneseReading", "reading",
                    "meaningVi", "test",
                    "meaningEn", "test",
                    "targetText", "target",
                    "displayOrder", itemNumber);
            case "pitch-accents" -> Map.of("readingId", readingId, "accentPattern", itemNumber);
            case "levels" -> Map.of("level", "N" + (5 - itemNumber), "displayOrder", itemNumber);
            case "lessons" -> Map.of(
                    "lessonId",
                    queryLong(
                            "SELECT l.id FROM lessons l JOIN jlpt_levels j ON"
                                    + " j.id=l.level_id WHERE j.code='N5' AND lesson_number="
                                    + itemNumber),
                    "displayOrder",
                    itemNumber);
            case "parts-of-speech" -> Map.of("code", List.of("NOUN", "VERB", "I_ADJECTIVE").get(itemNumber - 1));
            case "kanji" -> Map.of(
                    "character",
                    fixtureSuffix.substring(0, 7) + itemNumber,
                    "strokeCount",
                    3,
                    "meaningVi",
                    "test",
                    "meaningEn",
                    "test",
                    "displayOrder",
                    itemNumber);
            case "kanji-readings" -> Map.of(
                    "reading",
                    "reading-" + itemNumber,
                    "readingType",
                    "ON",
                    "displayOrder",
                    itemNumber);
            default -> throw new IllegalArgumentException(section);
        };
    }

    private String queryEditedField(String section, long editedResourceId) {
        String query =
                switch (section) {
                    case "readings" -> "SELECT reading FROM vocabulary_readings WHERE id=" + editedResourceId;
                    case "meanings" -> "SELECT meaning FROM vocabulary_meanings WHERE id=" + editedResourceId;
                    case "examples" -> "SELECT japanese_text FROM example_sentences WHERE id="
                            + editedResourceId;
                    case "pitch-accents" -> "SELECT accent_pattern FROM vocabulary_pitch_accents WHERE id="
                            + editedResourceId;
                    case "levels" -> "SELECT display_order FROM vocabulary_levels WHERE vocabulary_id="
                            + vocabularyId
                            + " AND level_id="
                            + editedResourceId;
                    case "lessons" -> "SELECT display_order FROM lesson_vocabulary WHERE vocabulary_id="
                            + vocabularyId
                            + " AND lesson_id="
                            + editedResourceId;
                    case "kanji" -> "SELECT kanji_character FROM kanji WHERE id=" + editedResourceId;
                    case "kanji-readings" -> "SELECT reading FROM kanji_readings WHERE id=" + editedResourceId;
                    default -> throw new IllegalArgumentException(section);
                };
        return pool.query(query)
                .execute()
                .await()
                .atMost(Duration.ofSeconds(10))
                .iterator()
                .next()
                .getValue(0)
                .toString();
    }

    private long countSectionRows(String section) {
        String sql =
                switch (section) {
                    case "readings" -> "SELECT COUNT(*) FROM vocabulary_readings WHERE vocabulary_id="
                            + vocabularyId;
                    case "meanings" -> "SELECT COUNT(*) FROM vocabulary_meanings WHERE vocabulary_id="
                            + vocabularyId;
                    case "examples" -> "SELECT COUNT(*) FROM vocabulary_examples WHERE vocabulary_id="
                            + vocabularyId;
                    case "pitch-accents" -> "SELECT COUNT(*) FROM vocabulary_pitch_accents WHERE"
                            + " vocabulary_reading_id="
                            + readingId;
                    case "levels" -> "SELECT COUNT(*) FROM vocabulary_levels WHERE vocabulary_id="
                            + vocabularyId;
                    case "lessons" -> "SELECT COUNT(*) FROM lesson_vocabulary WHERE vocabulary_id="
                            + vocabularyId;
                    case "parts-of-speech" -> "SELECT COUNT(*) FROM vocabulary_parts_of_speech WHERE vocabulary_id="
                            + vocabularyId;
                    case "kanji" -> "SELECT COUNT(*) FROM vocabulary_kanji WHERE vocabulary_id="
                            + vocabularyId;
                    case "kanji-readings" -> "SELECT COUNT(*) FROM kanji_readings WHERE kanji_id=" + kanjiId;
                    default -> throw new IllegalArgumentException(section);
                };
        return queryLong(sql);
    }

    private ValidatableResponse sendAdminRequest(
            String method, long targetVocabularyId, String suffix, Object body) {
        return given().auth()
                .oauth2(token)
                .contentType("application/json")
                .body(body)
                .request(method, "/api/v1/admin/vocabularies/" + targetVocabularyId + suffix)
                .then();
    }

    private long seedVocabulary(String word) {
        executeSql(
                "INSERT INTO vocabulary(word,normalized_word) VALUES ('"
                        + word
                        + "','"
                        + word
                        + "')");
        return queryLong("SELECT id FROM vocabulary WHERE normalized_word='" + word + "'");
    }

    private void executeSql(String sql) {
        pool.query(sql).execute().await().atMost(Duration.ofSeconds(10));
    }

    private long queryLong(String sql) {
        return pool.query(sql)
                .execute()
                .await()
                .atMost(Duration.ofSeconds(10))
                .iterator()
                .next()
                .getLong(0);
    }

    private String queryText(String sql) {
        return pool.query(sql)
                .execute()
                .await()
                .atMost(Duration.ofSeconds(10))
                .iterator()
                .next()
                .getString(0);
    }
}
