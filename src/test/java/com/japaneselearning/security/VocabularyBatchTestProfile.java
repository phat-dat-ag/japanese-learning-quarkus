package com.japaneselearning.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.ExampleSentence;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.PartOfSpeech;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.VocabularyFileReader;
import com.japaneselearning.vocabulary.importer.VocabularyExampleImporter;
import com.japaneselearning.vocabulary.importer.VocabularyImporter;
import com.japaneselearning.vocabulary.importer.VocabularyLessonImporter;
import com.japaneselearning.vocabulary.importer.VocabularyPartOfSpeechImporter;
import com.japaneselearning.vocabulary.importer.VocabularyRelationImporter;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.VocabularyLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonVocabularyRepository;
import com.japaneselearning.vocabulary.repository.ExampleSentenceRepository;
import com.japaneselearning.vocabulary.repository.VocabularyExampleRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import com.japaneselearning.vocabulary.repository.PartOfSpeechRepository;
import com.japaneselearning.vocabulary.repository.VocabularyPartOfSpeechRepository;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import com.japaneselearning.vocabulary.service.VocabularyService;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.smallrye.mutiny.Uni;
import io.vertx.core.Context;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicLong;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class VocabularyBatchTestProfile implements QuarkusTestProfile {
    static final Map<String, Vocabulary> vocabularies = new ConcurrentHashMap<>();

    static final Map<Long, VocabularyImportItem> creations = new ConcurrentHashMap<>();
    static final Map<Long, List<VocabularyExample>> examples = new ConcurrentHashMap<>();
    static final Map<Long, ExampleSentence> sentences = new ConcurrentHashMap<>();
    static final AtomicLong sentenceIds = new AtomicLong();

    static void reset() {
        vocabularies.clear();
        creations.clear();
        examples.clear();
        sentences.clear();
        sentenceIds.set(0);
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("vocabulary.import.max-batch-size", "2");
    }

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(TestService.class, TestVocabularyRepository.class, TestRelations.class,
                TestLevels.class, TestLessons.class, TestPartsOfSpeech.class, TestPosLinks.class, TestFileReader.class,
                TestLevelAssignments.class, TestLessonAssignments.class, TestSentences.class, TestExampleLinks.class);
    }

    // Keep HTTP, JWT, parsing, validation, core creation and lesson lookup real.
    // Like the existing API tests, replace database access and omit the DB transaction interceptor.
    @Alternative
    @Singleton
    public static class TestService extends VocabularyService {
        private final VocabularyImporter importer;

        public TestService(VocabularyImporter importer) {
            super(importer);
            this.importer = importer;
        }

        @Override
        public Uni<ImportResult> importVocabulary(Path file) {
            return importer.importVocabulary(file);
        }

        @Override
        public Uni<ImportResult> importVocabulary(List<VocabularyImportItem> items) {
            return importer.importVocabulary(items);
        }
    }

    @Alternative
    @Singleton
    public static class TestFileReader extends VocabularyFileReader {
        public TestFileReader(ObjectMapper mapper) {
            super(mapper);
        }

        @Override
        public List<VocabularyImportItem> read(Path file) throws IOException {
            assertTrue(Context.isOnWorkerThread(), "File I/O must run off the event loop");
            return super.read(file);
        }
    }

    @Alternative
    @Singleton
    public static class TestVocabularyRepository extends VocabularyRepository {
        @Override
        public Uni<Vocabulary> findByNormalizedWord(String word) {
            assertTrue(Context.isOnEventLoopThread(), "Persistence must resume on the event loop");
            return Uni.createFrom().item(vocabularies.get(word));
        }

        @Override
        public Uni<Vocabulary> persistAndFlush(Vocabulary vocabulary) {
            vocabulary.id = (long) vocabularies.size() + 1;
            vocabularies.put(vocabulary.normalizedWord, vocabulary);
            return Uni.createFrom().item(vocabulary);
        }
    }

    @Alternative
    @Singleton
    public static class TestRelations extends VocabularyRelationImporter {
        private final VocabularyPartOfSpeechImporter partsOfSpeech;
        private final VocabularyExampleImporter examplesImporter;

        public TestRelations(VocabularyLessonImporter lessons, VocabularyPartOfSpeechImporter partsOfSpeech,
                             VocabularyExampleImporter examplesImporter) {
            super(null, null, null, null, null, null, null, lessons);
            this.partsOfSpeech = partsOfSpeech;
            this.examplesImporter = examplesImporter;
        }

        @Override
        public Uni<Void> importRelations(Vocabulary vocabulary, VocabularyImportItem item) {
            if (creations.putIfAbsent(vocabulary.id, item) != null) {
                throw new AssertionError("Existing vocabulary must never reach relation creation");
            }
            return partsOfSpeech.importPartsOfSpeech(vocabulary, item)
                    .chain(() -> examplesImporter.importExamples(vocabulary, item));
        }
    }

    @Alternative
    @Singleton
    public static class TestLevels extends JlptLevelRepository {
        @Override
        public Uni<JlptLevel> findByCode(String code) {
            JlptLevel level = new JlptLevel();
            level.id = 5L;
            level.code = code;
            return Uni.createFrom().item(level);
        }
    }

    @Alternative
    @Singleton
    public static class TestLessons extends LessonRepository {
        @Override
        public Uni<Lesson> findByLevelIdAndLessonNumber(Long levelId, Integer number) {
            Lesson lesson = new Lesson();
            lesson.id = 1L;
            lesson.levelId = levelId;
            lesson.lessonNumber = 1;
            lesson.lessonNumber = number;
            return Uni.createFrom().item(number <= 2 ? lesson : null);
        }

        @Override
        public Uni<Lesson> persist(Lesson lesson) {
            throw new AssertionError("Vocabulary import must never create lessons");
        }
    }

    @Alternative
    @Singleton
    public static class TestPartsOfSpeech extends PartOfSpeechRepository {
        @Override
        public Uni<PartOfSpeech> findByCode(String code) {
            PartOfSpeech pos = new PartOfSpeech();
            pos.id = 1L;
            pos.code = "NOUN";
            return Uni.createFrom().item("NOUN".equals(code) ? pos : null);
        }
    }

    @Alternative
    @Singleton
    public static class TestPosLinks extends VocabularyPartOfSpeechRepository {
        @Override
        public Uni<Void> insert(Long vocabularyId, Long posId) {
            return Uni.createFrom().voidItem();
        }
    }

    @Alternative
    @Singleton
    public static class TestLevelAssignments extends VocabularyLevelRepository {
        @Override
        public Uni<List<String>> findLevelCodes(Long vocabularyId) {
            return Uni.createFrom().item(creations.get(vocabularyId).levels);
        }
    }

    @Alternative
    @Singleton
    public static class TestLessonAssignments extends LessonVocabularyRepository {
        @Override
        public Uni<List<Lesson>> findLessons(Long vocabularyId) {
            return Uni.createFrom().item(creations.get(vocabularyId).lessons.stream().map(item -> {
                Lesson lesson = new Lesson();
                lesson.lessonNumber = item.lessonNumber;
                lesson.level = new JlptLevel();
                lesson.level.code = item.level;
                return lesson;
            }).toList());
        }
    }

    @Alternative
    @Singleton
    public static class TestSentences extends ExampleSentenceRepository {
        @Override
        public Uni<ExampleSentence> persist(ExampleSentence sentence) {
            sentence.id = sentenceIds.incrementAndGet();
            sentences.put(sentence.id, sentence);
            return Uni.createFrom().item(sentence);
        }
    }

    @Alternative
    @Singleton
    public static class TestExampleLinks extends VocabularyExampleRepository {
        @Override
        public Uni<List<VocabularyExample>> findByVocabularyId(Long vocabularyId) {
            return Uni.createFrom().item(examples.getOrDefault(vocabularyId, List.of()));
        }

        @Override
        public Uni<Void> insert(Long vocabularyId, Long sentenceId, String targetText, Integer displayOrder) {
            VocabularyExample link = new VocabularyExample();
            link.vocabularyId = vocabularyId;
            link.exampleSentenceId = sentenceId;
            link.exampleSentence = sentences.get(sentenceId);
            link.targetText = targetText;
            link.displayOrder = displayOrder;
            examples.computeIfAbsent(vocabularyId, ignored -> new ArrayList<>()).add(link);
            return Uni.createFrom().voidItem();
        }
    }

}
