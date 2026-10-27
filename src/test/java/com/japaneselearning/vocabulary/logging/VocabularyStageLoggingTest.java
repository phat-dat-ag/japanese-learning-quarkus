package com.japaneselearning.vocabulary.logging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.vocabulary.admin.dto.VocabularyCoreUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.VocabularyReadingUpdateRequest;
import com.japaneselearning.vocabulary.admin.service.VocabularyCoreEditService;
import com.japaneselearning.vocabulary.admin.service.VocabularyEditPersistence;
import com.japaneselearning.vocabulary.admin.service.VocabularyReadingEditService;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import com.japaneselearning.vocabulary.repository.VocabularyReadingRepository;
import com.japaneselearning.vocabulary.importer.VocabularyImportValidator;
import com.japaneselearning.vocabulary.entity.ExampleSentence;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.importer.VocabularyKanjiImporter;
import com.japaneselearning.vocabulary.importer.dto.VocabularyKanjiImportItem;
import com.japaneselearning.vocabulary.repository.KanjiRepository;
import com.japaneselearning.vocabulary.repository.VocabularyKanjiRepository;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import com.japaneselearning.vocabulary.importer.VocabularyExampleImporter;
import com.japaneselearning.vocabulary.importer.dto.VocabularyExampleImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.ExampleSentenceRepository;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.VocabularyExampleRepository;
import com.japaneselearning.vocabulary.service.JlptLevelService;
import com.japaneselearning.vocabulary.service.LessonBatchFileReader;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.core.Vertx;
import jakarta.ws.rs.BadRequestException;
import org.jboss.logmanager.ExtLogRecord;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VocabularyStageLoggingTest {
    @TempDir
    Path directory;

    private final List<ExtLogRecord> records = new ArrayList<>();
    private final Logger logger = Logger.getLogger("com.japaneselearning.vocabulary");
    private Level previousLevel;
    private final Handler handler = new Handler() {
        public void publish(LogRecord record) {
            ExtLogRecord extended = (ExtLogRecord) record;
            extended.copyMdc();
            records.add(extended);
        }

        public void flush() {
        }

        public void close() {
        }
    };

    @BeforeEach
    void capture() {
        previousLevel = logger.getLevel();
        logger.setLevel(Level.FINE);
        logger.addHandler(handler);
    }

    @AfterEach
    void release() {
        logger.removeHandler(handler);
        logger.setLevel(previousLevel);
    }

    @Test
    void jlptSummaryWaitsForRetrievalAndUsesActualCountWithoutEntityText() {
        CompletableFuture<List<JlptLevel>> gate = new CompletableFuture<>();
        var service = new JlptLevelService(new JlptLevelRepository() {
            @Override
            public Uni<List<JlptLevel>> findAllOrdered() {
                return Uni.createFrom().completionStage(() -> gate);
            }
        });
        var result = service.getLevels().subscribeAsCompletionStage();
        assertTrue(records.isEmpty());
        var level = new JlptLevel();
        level.code = "N3";
        level.name = "secret-catalogue-name";
        gate.complete(List.of(level));
        assertEquals(1, result.join().size());
        assertEquals(1, records.size());
        assertEquals("jlpt.levels.retrieved", records.get(0).getMdc("event.name"));
        assertEquals("1", records.get(0).getMdc("count"));
        assertEquals("Successfully retrieved 1 JLPT levels", records.get(0).getFormattedMessage());
        assertEquals("INFO", records.get(0).getLevel().getName());
        assertFalse(records.get(0).getFormattedMessage().contains("secret"));
    }

    @Test
    void failedJlptRetrievalEmitsNoSuccessOrDuplicateException() {
        var service = new JlptLevelService(new JlptLevelRepository() {
            @Override
            public Uni<List<JlptLevel>> findAllOrdered() {
                return Uni.createFrom().failure(new IllegalStateException("secret-database-error"));
            }
        });
        assertThrows(IllegalStateException.class, () -> service.getLevels().await().indefinitely());
        assertTrue(records.isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "null", "[]", "{secret", "[{}] {}"})
    void invalidLessonFilesProduceOneSafeWarning(String body) throws Exception {
        Path file = directory.resolve("secret-upload-name.json");
        Files.writeString(file, body);
        Vertx vertx = Vertx.vertx();
        try {
            var reader = new LessonBatchFileReader(vertx, new ObjectMapper());
            assertThrows(BadRequestException.class,
                    () -> reader.read(file).await().atMost(Duration.ofSeconds(5)));
            assertEquals(1, records.size());
            assertEquals("lesson.batch.file.rejected", records.get(0).getMdc("event.name"));
            assertEquals("WARN", records.get(0).getLevel().getName());
            assertFalse(records.get(0).getFormattedMessage().contains("secret"));
            assertNull(records.get(0).getThrown());
        } finally {
            vertx.close().await().atMost(Duration.ofSeconds(5));
        }
    }

    @Test
    void parsedLessonBatchHasActualCountBeforeItemValidation() throws Exception {
        Path file = directory.resolve("secret-upload-name.json");
        Files.writeString(file, "[{},null]");
        Vertx vertx = Vertx.vertx();
        try {
            var result = new LessonBatchFileReader(vertx, new ObjectMapper())
                    .read(file).await().atMost(Duration.ofSeconds(5));
            assertEquals(2, result.size());
            assertEquals(1, records.size());
            assertEquals("lesson.batch.file.parsed", records.get(0).getMdc("event.name"));
            assertEquals("2", records.get(0).getMdc("total"));
            assertTrue(records.get(0).getFormattedMessage().contains("committed independently"));
        } finally {
            vertx.close().await().atMost(Duration.ofSeconds(5));
        }
    }

    @Test
    void exampleDeduplicationSummaryCountsExistingAndRepeatedInputWithoutLoggingPayload() {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.id = 25L;
        var existing = new VocabularyExample();
        existing.exampleSentence = new ExampleSentence();
        existing.exampleSentence.japaneseText = "secret-existing";
        existing.exampleSentence.japaneseReading = "secret-reading";
        existing.targetText = "secret-target";
        List<ExampleSentence> inserted = new ArrayList<>();
        var importer = new VocabularyExampleImporter(new ExampleSentenceRepository() {
            @Override
            public Uni<ExampleSentence> persist(ExampleSentence sentence) {
                sentence.id = 30L;
                inserted.add(sentence);
                return Uni.createFrom().item(sentence);
            }
        }, new VocabularyExampleRepository() {
            @Override
            public Uni<List<VocabularyExample>> findByVocabularyId(Long id) {
                return Uni.createFrom().item(List.of(existing));
            }

            @Override
            public Uni<Void> insert(Long id, Long exampleId, String target, Integer order) {
                return Uni.createFrom().voidItem();
            }
        });
        VocabularyImportItem item = new VocabularyImportItem();
        item.examples = List.of(example("secret-existing"), example("secret-new"), example("secret-new"));
        importer.importExamples(vocabulary, item).await().indefinitely();
        assertEquals(1, inserted.size());
        assertEquals(1, records.size());
        ExtLogRecord record = records.get(0);
        assertEquals("vocabulary.import.examples.prepared", record.getMdc("event.name"));
        assertEquals("3", record.getMdc("total"));
        assertEquals("1", record.getMdc("created"));
        assertEquals("2", record.getMdc("count"));
        assertTrue(record.getFormattedMessage().contains("inserted=1, skipped=2"));
        assertTrue(record.getFormattedMessage().contains("awaiting vocabulary transaction completion"));
        assertFalse(record.getFormattedMessage().contains("secret"));
    }

    @Test
    void duplicateCoreIdentityExplainsConflictWithoutLoggingNormalizedWord() {
        var service = new VocabularyCoreEditService(persistence(), new VocabularyRepository() {
            @Override
            public Uni<Vocabulary> findByNormalizedWord(String word) {
                Vocabulary duplicate = new Vocabulary();
                duplicate.id = 99L;
                return Uni.createFrom().item(duplicate);
            }
        });
        assertThrows(ConflictException.class, () ->
                service.updateVocabularyCore(25L, new VocabularyCoreUpdateRequest("secret", "secret-normalized"))
                        .await().indefinitely());
        assertEquals(1, records.size());
        assertEquals("vocabulary.core.uniqueness_checked", records.get(0).getMdc("event.name"));
        assertEquals("duplicate_normalized_word", records.get(0).getMdc("reason"));
        assertFalse(records.get(0).getFormattedMessage().contains("secret"));
    }

    @Test
    void primaryReadingRestrictionLogsActualRemainingCount() {
        var service = new VocabularyReadingEditService(persistence(), new VocabularyReadingRepository() {
            @Override
            public Uni<VocabularyReading> findReadingForVocabulary(Long vocabularyId, Long readingId) {
                VocabularyReading reading = new VocabularyReading();
                reading.id = readingId;
                return Uni.createFrom().item(reading);
            }

            @Override
            public Uni<VocabularyReading> findByVocabularyIdAndReading(Long vocabularyId, String reading) {
                return Uni.createFrom().nullItem();
            }

            @Override
            public Uni<Long> countPrimaryReadingsExcluding(Long vocabularyId, Long readingId) {
                return Uni.createFrom().item(0L);
            }
        });
        assertThrows(ValidationException.class, () ->
                service.updateVocabularyReading(25L, 30L,
                                new VocabularyReadingUpdateRequest("secret-reading", false, 1))
                        .await().indefinitely());
        assertEquals(1, records.size());
        assertEquals("vocabulary.reading.primary_checked", records.get(0).getMdc("event.name"));
        assertEquals("0", records.get(0).getMdc("count"));
        assertEquals("25", records.get(0).getMdc("vocabularyId"));
        assertFalse(records.get(0).getFormattedMessage().contains("secret"));
    }

    @Test
    void duplicateInputDiagnosticContainsNoVocabularyText() throws Exception {
        VocabularyImportItem item;
        try (var input = getClass().getResourceAsStream("/data/vocabulary/reader-fixture.json")) {
            item = new ObjectMapper().readValue(input, VocabularyImportItem[].class)[0];
        }
        item.word = "secret-word";
        item.normalizedWord = "secret-normalized";
        var example = example("secret-sentence");
        example.meaningVi = "secret-translation";
        example.meaningEn = "secret-translation";
        item.examples = List.of(example);
        var failure = assertThrows(IllegalArgumentException.class, () ->
                new VocabularyImportValidator().validate(List.of(item, item)));
        assertTrue(failure.getMessage().startsWith("Duplicate vocabulary in import file:"));
        assertEquals(1, records.size());
        assertEquals("vocabulary.import.duplicate_detected", records.get(0).getMdc("event.name"));
        assertEquals("2", records.get(0).getMdc("total"));
        assertFalse(records.get(0).getFormattedMessage().contains("secret"));
    }

    @Test
    void importKanjiReuseDescribesPendingMetadataChangesWithoutContent() {
        Kanji kanji = new Kanji();
        kanji.id = 9L;
        var importer = new VocabularyKanjiImporter(new KanjiRepository() {
            @Override
            public Uni<Kanji> findByCharacter(String character) {
                return Uni.createFrom().item(kanji);
            }
        }, null, new VocabularyKanjiRepository() {
            @Override
            public Uni<Void> insert(Long vocabularyId, Long kanjiId, Integer displayOrder) {
                return Uni.createFrom().voidItem();
            }
        });
        var vocabulary = new Vocabulary();
        vocabulary.id = 25L;
        var item = new VocabularyImportItem();
        var importedKanji = new VocabularyKanjiImportItem();
        importedKanji.character = "secret-character";
        importedKanji.meaningEn = "secret-meaning";
        importedKanji.strokeCount = 8;
        item.kanji = List.of(importedKanji);
        importer.importKanji(vocabulary, item).await().indefinitely();
        assertEquals(8, kanji.strokeCount);
        assertEquals("secret-meaning", kanji.meaningEn);
        assertEquals(1, records.size());
        assertEquals("vocabulary.import.kanji.reused", records.get(0).getMdc("event.name"));
        assertEquals("9", records.get(0).getMdc("kanjiId"));
        assertTrue(records.get(0).getFormattedMessage().contains("await vocabulary transaction completion"));
        assertFalse(records.get(0).getFormattedMessage().contains("secret"));
    }

    private VocabularyEditPersistence persistence() {
        return new VocabularyEditPersistence(null) {
            @Override
            public Uni<Vocabulary> requireVocabularyForUpdate(Long id) {
                Vocabulary vocabulary = new Vocabulary();
                vocabulary.id = id;
                return Uni.createFrom().item(vocabulary);
            }

            @Override
            public <T> Uni<T> flushAndMapUniqueConflicts(Uni<T> operation) {
                return operation;
            }
        };
    }

    private VocabularyExampleImportItem example(String text) {
        var example = new VocabularyExampleImportItem();
        example.japaneseText = text;
        example.japaneseReading = "secret-reading";
        example.targetText = "secret-target";
        return example;
    }
}
