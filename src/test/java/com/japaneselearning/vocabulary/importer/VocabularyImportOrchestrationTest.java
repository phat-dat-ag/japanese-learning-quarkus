package com.japaneselearning.vocabulary.importer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VocabularyImportOrchestrationTest {

    @TempDir
    Path directory;

    private final List<String> calls = new ArrayList<>();
    private IllegalArgumentException validationFailure;
    private String failingStage;
    private final IllegalStateException persistenceFailure = new IllegalStateException("Persistence failed");
    private CompletableFuture<Void> levelGate;

    @Test
    void preservesImportOrderAndExistingResultCounts() throws IOException {
        ImportResult result = importer().importVocabulary(inputFile())
                .await().atMost(Duration.ofSeconds(1));

        assertEquals(new ImportResult(2, 0, 2), result);
        assertEquals(List.of(
                "validate", "core:first", "levels", "lessons", "readings", "meanings",
                "partsOfSpeech", "kanji", "pitchAccents", "examples",
                "core:second", "levels", "lessons", "readings", "meanings",
                "partsOfSpeech", "kanji", "pitchAccents", "examples"
        ), calls);
    }

    @Test
    void waitsForARelationBeforeStartingLaterRelationsOrItems() throws IOException {
        levelGate = new CompletableFuture<>();
        CompletableFuture<ImportResult> result = importer().importVocabulary(inputFile())
                .subscribeAsCompletionStage();

        assertEquals(List.of("validate", "core:first", "levels"), calls);
        assertFalse(result.isDone());

        levelGate.complete(null);

        assertEquals(new ImportResult(2, 0, 2), result.join());
        assertEquals("examples", calls.get(calls.size() - 1));
    }

    @Test
    void stopsAfterPersistenceFailure() throws IOException {
        failingStage = "meanings";
        Path file = inputFile();

        assertSame(persistenceFailure, assertThrows(
                IllegalStateException.class,
                () -> importer().importVocabulary(file).await().atMost(Duration.ofSeconds(1))
        ));
        assertEquals(List.of("validate", "core:first", "levels", "lessons", "readings", "meanings"), calls);
    }

    @Test
    void mapsValidationFailureWithoutWritingData() throws IOException {
        validationFailure = new IllegalArgumentException("Invalid vocabulary");
        Path file = inputFile();

        ValidationException failure = assertThrows(ValidationException.class,
                () -> importer().importVocabulary(file).await().atMost(Duration.ofSeconds(1)));
        assertEquals("VALIDATION_ERROR", failure.getCode());
        assertEquals("Vocabulary file contains invalid data", failure.getErrors().get(0).message());
        assertEquals(List.of("validate"), calls);
    }

    @Test
    void mapsMalformedJsonWithoutExposingParserDetails() throws IOException {
        Path file = directory.resolve("invalid.json");
        Files.writeString(file, "invalid JSON");

        ValidationException failure = assertThrows(ValidationException.class,
                () -> importer().importVocabulary(file).await().atMost(Duration.ofSeconds(1)));
        assertEquals("VALIDATION_ERROR", failure.getCode());
        assertEquals("File must contain valid vocabulary JSON", failure.getErrors().get(0).message());
        org.junit.jupiter.api.Assertions.assertNull(failure.getCause());
        assertEquals(List.of(), calls);
    }

    @Test
    void preservesMissingFileFailure() {
        RuntimeException failure = assertThrows(RuntimeException.class,
                () -> importer().importVocabulary(directory.resolve("missing.json"))
                        .await().atMost(Duration.ofSeconds(1)));
        assertInstanceOf(IOException.class, failure.getCause());
        assertEquals(List.of(), calls);
    }

    private Path inputFile() throws IOException {
        Path file = directory.resolve("vocabulary.json");
        Files.writeString(file, """
                [
                    {"word": "first", "normalizedWord": "first"},
                    {"word": "second", "normalizedWord": "second"}
                ]
                """);
        return file;
    }

    private Uni<Void> stage(String name) {
        calls.add(name);
        if (name.equals(failingStage)) {
            return Uni.createFrom().failure(persistenceFailure);
        }
        if (name.equals("levels") && levelGate != null) {
            return Uni.createFrom().completionStage(() -> levelGate);
        }
        return Uni.createFrom().voidItem();
    }

    private VocabularyImporter importer() {
        VocabularyImportValidator validator = new VocabularyImportValidator() {
            @Override
            public void validate(List<VocabularyImportItem> items) {
                calls.add("validate");
                if (validationFailure != null) {
                    throw validationFailure;
                }
            }
        };
        VocabularyCoreImporter core = new VocabularyCoreImporter(null) {
            @Override
            public Uni<Vocabulary> getOrCreate(VocabularyImportItem item) {
                calls.add("core:" + item.word);
                Vocabulary vocabulary = new Vocabulary();
                vocabulary.word = item.word;
                vocabulary.normalizedWord = item.normalizedWord;
                return Uni.createFrom().item(vocabulary);
            }
        };
        VocabularyRelationImporter relations = new VocabularyRelationImporter(
                new VocabularyLevelImporter(null, null) {
                    @Override
                    public Uni<Void> importLevels(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("levels");
                    }
                },
                new VocabularyReadingImporter(null) {
                    @Override
                    public Uni<Void> importReadings(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("readings");
                    }
                },
                new VocabularyMeaningImporter(null) {
                    @Override
                    public Uni<Void> importMeanings(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("meanings");
                    }
                },
                new VocabularyPartOfSpeechImporter(null, null) {
                    @Override
                    public Uni<Void> importPartsOfSpeech(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("partsOfSpeech");
                    }
                },
                new VocabularyKanjiImporter(null, null, null) {
                    @Override
                    public Uni<Void> importKanji(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("kanji");
                    }
                },
                new VocabularyPitchAccentImporter(null, null) {
                    @Override
                    public Uni<Void> importPitchAccents(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("pitchAccents");
                    }
                },
                new VocabularyExampleImporter(null, null) {
                    @Override
                    public Uni<Void> importExamples(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("examples");
                    }
                },
                new VocabularyLessonImporter(null, null, null) {
                    @Override
                    public Uni<Void> importLessons(Vocabulary vocabulary, VocabularyImportItem item) {
                        return stage("lessons");
                    }
                }
        );
        return new VocabularyImporter(
                new VocabularyFileReader(new ObjectMapper()), validator, core, relations
        );
    }
}
