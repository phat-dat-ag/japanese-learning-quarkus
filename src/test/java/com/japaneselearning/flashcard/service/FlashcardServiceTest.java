package com.japaneselearning.flashcard.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.VocabularyResponse;
import com.japaneselearning.flashcard.dto.FlashcardExampleResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiReadingResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiResponse;
import com.japaneselearning.flashcard.dto.FlashcardLessonResponse;
import com.japaneselearning.flashcard.dto.FlashcardLevelResponse;
import com.japaneselearning.flashcard.dto.FlashcardMeaningResponse;
import com.japaneselearning.flashcard.dto.FlashcardPartOfSpeechResponse;
import com.japaneselearning.flashcard.dto.FlashcardReadingResponse;
import com.japaneselearning.flashcard.dto.FlashcardPitchAccentResponse;
import com.japaneselearning.flashcard.repository.FlashcardRepository.LevelDetail;
import com.japaneselearning.flashcard.repository.FlashcardRepository.LessonDetail;
import com.japaneselearning.flashcard.repository.FlashcardRepository.KanjiDetail;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import com.japaneselearning.vocabulary.entity.PartOfSpeech;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.entity.ExampleSentence;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.NoResultException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlashcardServiceTest {

    private final StubRepository repository = new StubRepository();
    private final FlashcardService service = new FlashcardService(repository);

    FlashcardServiceTest() {
        repository.vocabulary.id = 42L;
        repository.vocabulary.word = "word";
        repository.vocabulary.normalizedWord = "normalized-word";
    }

    @Test
    void mapsEntitiesAndGroupsRelatedDataById() {
        VocabularyReading firstReading = new VocabularyReading();
        firstReading.id = 11L;
        firstReading.reading = "first";
        firstReading.isPrimary = true;
        firstReading.displayOrder = 3;
        VocabularyReading secondReading = new VocabularyReading();
        secondReading.id = 7L;
        secondReading.reading = "second";
        secondReading.isPrimary = false;
        secondReading.displayOrder = 9;
        repository.readings = List.of(firstReading, secondReading);

        VocabularyPitchAccent firstAccent = new VocabularyPitchAccent();
        firstAccent.id = 91L;
        firstAccent.vocabularyReadingId = 11L;
        firstAccent.accentPattern = 0;
        VocabularyPitchAccent secondAccent = new VocabularyPitchAccent();
        secondAccent.id = 83L;
        secondAccent.vocabularyReadingId = 11L;
        secondAccent.accentPattern = 2;
        VocabularyPitchAccent otherAccent = new VocabularyPitchAccent();
        otherAccent.id = 107L;
        otherAccent.vocabularyReadingId = 7L;
        otherAccent.accentPattern = 2;
        repository.pitchAccents = List.of(otherAccent, firstAccent, secondAccent);

        VocabularyMeaning meaning = new VocabularyMeaning();
        meaning.id = 71L;
        meaning.displayOrder = 4;
        meaning.languageCode = "en";
        meaning.meaning = "meaning";
        meaning.isPrimary = true;
        VocabularyMeaning otherMeaning = new VocabularyMeaning();
        otherMeaning.id = 63L;
        otherMeaning.languageCode = "vi";
        otherMeaning.meaning = "meaning";
        otherMeaning.isPrimary = false;
        otherMeaning.displayOrder = 8;
        repository.meanings = List.of(meaning, otherMeaning);

        PartOfSpeech pos = new PartOfSpeech();
        pos.code = "noun";
        pos.nameVi = "noun-vi";
        pos.nameEn = "noun-en";
        repository.partsOfSpeech = List.of(pos);

        JlptLevel level = new JlptLevel();
        level.id = 5L;
        level.code = "N5";
        level.name = "Level N5";
        repository.levels = List.of(new LevelDetail(level, null));

        JlptLevel lessonLevel = new JlptLevel();
        lessonLevel.code = "N4";
        lessonLevel.name = "Level N4";
        Lesson lesson = new Lesson();
        lesson.id = 35L;
        lesson.level = lessonLevel;
        lesson.lessonNumber = 3;
        lesson.title = "Lesson";
        lesson.description = null;
        lesson.displayOrder = 7;
        repository.lessons = List.of(new LessonDetail(lesson, 19));

        Kanji firstKanji = new Kanji();
        firstKanji.id = 21L;
        firstKanji.character = "first-kanji";
        firstKanji.strokeCount = null;
        firstKanji.meaningVi = "kanji-vi";
        firstKanji.meaningEn = "kanji-en";
        Kanji secondKanji = new Kanji();
        secondKanji.id = 22L;
        secondKanji.character = "second-kanji";
        secondKanji.strokeCount = 4;
        repository.kanji = List.of(new KanjiDetail(firstKanji, 4), new KanjiDetail(secondKanji, 10));

        KanjiReading kanjiReading = new KanjiReading();
        kanjiReading.id = 57L;
        kanjiReading.displayOrder = 6;
        kanjiReading.kanjiId = 22L;
        kanjiReading.reading = "kanji-reading";
        kanjiReading.readingType = "on";
        repository.kanjiReadings = List.of(kanjiReading);

        ExampleSentence sentence = new ExampleSentence();
        sentence.japaneseText = "sentence";
        sentence.japaneseReading = "sentence-reading";
        sentence.meaningVi = "sentence-vi";
        sentence.meaningEn = "sentence-en";
        VocabularyExample example = new VocabularyExample();
        example.exampleSentenceId = 101L;
        example.displayOrder = 12;
        example.exampleSentence = sentence;
        example.targetText = "target";
        repository.examples = List.of(example);

        FlashcardDetailResponse response = service.getFlashcard(42L)
                .await().atMost(Duration.ofSeconds(1));

        assertEquals(new FlashcardDetailResponse(
                new VocabularyResponse(42L, "word", "normalized-word"),
                List.of(
                        new FlashcardReadingResponse(11L, "first", true, 3, List.of(0, 2), List.of(
                                new FlashcardPitchAccentResponse(91L, 0), new FlashcardPitchAccentResponse(83L, 2))),
                        new FlashcardReadingResponse(7L, "second", false, 9, List.of(2),
                                List.of(new FlashcardPitchAccentResponse(107L, 2)))
                ),
                List.of(new FlashcardMeaningResponse(71L, "en", "meaning", true, 4),
                        new FlashcardMeaningResponse(63L, "vi", "meaning", false, 8)),
                List.of(new FlashcardPartOfSpeechResponse("noun", "noun-vi", "noun-en")),
                List.of(new FlashcardLevelResponse(5L, "N5", "Level N5", null)),
                List.of(new FlashcardLessonResponse(35L, "N4", "Level N4", 3, "Lesson", null, 7, 19)),
                List.of(
                        new FlashcardKanjiResponse(
                                21L, "first-kanji", null, "kanji-vi", "kanji-en", List.of(), 4
                        ),
                        new FlashcardKanjiResponse(
                                22L, "second-kanji", 4, null, null,
                                List.of(new FlashcardKanjiReadingResponse(57L, "kanji-reading", "on", 6)), 10
                        )
                ),
                List.of(new FlashcardExampleResponse(
                        101L, "sentence", "sentence-reading", "sentence-vi", "sentence-en", "target", 12
                ))
        ), response);
        assertEquals(List.of(11L, 7L), repository.requestedReadingIds);
        assertEquals(List.of(21L, 22L), repository.requestedKanjiIds);
    }

    @Test
    void returnsEmptyListsWhenVocabularyHasNoRelatedData() {
        assertEquals(new FlashcardDetailResponse(
                new VocabularyResponse(42L, "word", "normalized-word"), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of()
        ), service.getFlashcard(42L).await().atMost(Duration.ofSeconds(1)));
        assertEquals(List.of(), repository.requestedReadingIds);
        assertEquals(List.of(), repository.requestedKanjiIds);
    }

    @Test
    void mapsMissingVocabularyWithoutLoadingRelations() {
        repository.failure = new NoResultException("Missing vocabulary");
        ResourceNotFoundException exception = assertThrows(
                ResourceNotFoundException.class,
                () -> service.getFlashcard(42L).await().atMost(Duration.ofSeconds(1))
        );
        assertEquals("VOCABULARY_NOT_FOUND", exception.getCode());
        assertEquals("Vocabulary 42 not found", exception.getMessage());
        assertEquals(0, repository.relationCalls);
    }

    @Test
    void serializesNormalizedWordInsideVocabulary() throws Exception {
        FlashcardDetailResponse response = service.getFlashcard(42L)
                .await().atMost(Duration.ofSeconds(1));
        ObjectMapper mapper = new ObjectMapper();

        assertEquals(
                mapper.readTree("""
                        {"id":42,"word":"word","normalizedWord":"normalized-word"}
                        """),
                mapper.readTree(mapper.writeValueAsString(response)).get("vocabulary")
        );
    }

    @Test
    void rejectsMissingVocabularyIdBeforeRepositoryAccess() {
        ValidationException exception = assertThrows(
                ValidationException.class, () -> service.getFlashcard(null)
        );
        assertEquals("FLASHCARD_VALIDATION_ERROR", exception.getCode());
        assertEquals("id", exception.getErrors().get(0).field());
        assertEquals(0, repository.relationCalls);
    }

    @Test
    void keepsDetailAssemblyIndependentAcrossSubscriptions() {
        CompletableFuture<List<VocabularyReading>> firstReadings = new CompletableFuture<>();
        CompletableFuture<List<VocabularyReading>> secondReadings = new CompletableFuture<>();
        StubRepository isolatedRepository = new StubRepository() {
            private int subscriptions;

            @Override
            public Uni<List<VocabularyReading>> findReadings(Long vocabularyId) {
                CompletableFuture<List<VocabularyReading>> readings =
                        subscriptions++ == 0 ? firstReadings : secondReadings;
                return Uni.createFrom().completionStage(() -> readings);
            }
        };
        isolatedRepository.vocabulary = repository.vocabulary;
        FlashcardService isolatedService = new FlashcardService(isolatedRepository);
        Uni<FlashcardDetailResponse> detail = isolatedService.getFlashcard(42L);

        CompletableFuture<FlashcardDetailResponse> first = detail.subscribeAsCompletionStage();
        CompletableFuture<FlashcardDetailResponse> second = detail.subscribeAsCompletionStage();

        VocabularyReading secondReading = new VocabularyReading();
        secondReading.id = 7L;
        secondReading.reading = "second";
        secondReadings.complete(List.of(secondReading));

        VocabularyReading firstReading = new VocabularyReading();
        firstReading.id = 11L;
        firstReading.reading = "first";
        firstReadings.complete(List.of(firstReading));

        assertEquals("first", first.join().readings().get(0).reading());
        assertEquals("second", second.join().readings().get(0).reading());
    }

    @Test
    void emptyBatchQueriesDoNotRequireADatabaseSession() {
        FlashcardRepository realRepository = new FlashcardRepository();
        assertEquals(List.of(), realRepository.findPitchAccentsByReadingIds(List.of())
                .await().atMost(Duration.ofSeconds(1)));
        assertEquals(List.of(), realRepository.findKanjiReadings(List.of())
                .await().atMost(Duration.ofSeconds(1)));
    }

    private static class StubRepository extends FlashcardRepository {
        Vocabulary vocabulary = new Vocabulary();
        RuntimeException failure;
        int relationCalls;
        List<Long> requestedReadingIds;
        List<Long> requestedKanjiIds;
        List<VocabularyReading> readings = List.of();
        List<VocabularyMeaning> meanings = List.of();
        List<PartOfSpeech> partsOfSpeech = List.of();
        List<LevelDetail> levels = List.of();
        List<LessonDetail> lessons = List.of();
        List<KanjiDetail> kanji = List.of();
        List<VocabularyExample> examples = List.of();
        List<VocabularyPitchAccent> pitchAccents = List.of();
        List<KanjiReading> kanjiReadings = List.of();

        @Override
        public Uni<Vocabulary> findVocabularyById(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            return failure == null ? Uni.createFrom().item(vocabulary) : Uni.createFrom().failure(failure);
        }

        @Override
        public Uni<List<VocabularyReading>> findReadings(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(readings));
        }

        @Override
        public Uni<List<VocabularyMeaning>> findMeanings(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(meanings));
        }

        @Override
        public Uni<List<PartOfSpeech>> findPartsOfSpeech(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(partsOfSpeech));
        }

        @Override
        public Uni<List<LevelDetail>> findLevels(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(levels));
        }

        @Override
        public Uni<List<LessonDetail>> findLessons(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(lessons));
        }

        @Override
        public Uni<List<KanjiDetail>> findKanji(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(kanji));
        }

        @Override
        public Uni<List<VocabularyExample>> findExamples(Long vocabularyId) {
            assertEquals(42L, vocabularyId);
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(examples));
        }

        @Override
        public Uni<List<VocabularyPitchAccent>> findPitchAccentsByReadingIds(List<Long> ids) {
            requestedReadingIds = ids;
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(pitchAccents));
        }

        @Override
        public Uni<List<KanjiReading>> findKanjiReadings(List<Long> ids) {
            requestedKanjiIds = ids;
            relationCalls++;
            return Uni.createFrom().deferred(() -> Uni.createFrom().item(kanjiReadings));
        }
    }
}
