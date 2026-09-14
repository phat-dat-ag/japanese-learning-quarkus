package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.VocabularyResponse;
import com.japaneselearning.flashcard.dto.FlashcardLessonResponse;
import com.japaneselearning.flashcard.dto.FlashcardExampleResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiReadingResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiResponse;
import com.japaneselearning.flashcard.dto.FlashcardLevelResponse;
import com.japaneselearning.flashcard.dto.FlashcardListItemResponse;
import com.japaneselearning.flashcard.dto.FlashcardListResponse;
import com.japaneselearning.flashcard.dto.FlashcardMeaningResponse;
import com.japaneselearning.flashcard.dto.FlashcardPartOfSpeechResponse;
import com.japaneselearning.flashcard.dto.FlashcardReadingResponse;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.entity.PartOfSpeech;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.NoResultException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class FlashcardService {

    private static final Set<String> SUPPORTED_LEVELS = Set.of("N1", "N2", "N3", "N4", "N5");

    @Inject
    FlashcardRepository flashcardRepository;

    // ============================================================
    // GET FLASHCARD LIST
    // ============================================================

    @WithSession
    public Uni<FlashcardListResponse> getFlashcards(
            String level,
            Integer lesson,
            int page,
            int size
    ) {
        String levelCode = normalizeLevel(level);
        validateListRequest(levelCode, lesson, page, size);
        int offset = page * size;

        Uni<List<Vocabulary>> vocabularyUni =
                flashcardRepository.findVocabulary(levelCode, lesson, offset, size);

        return vocabularyUni.flatMap(vocabulary -> {
            Uni<Long> countUni = flashcardRepository.countVocabulary(levelCode, lesson);

            return countUni.map(totalElements -> {
                List<FlashcardListItemResponse> items = vocabulary.stream()
                        .map(item -> new FlashcardListItemResponse(item.id, item.word))
                        .toList();

                int totalPages = (int) Math.ceil((double) totalElements / size);

                return new FlashcardListResponse(
                        items, page, size, totalElements, totalPages
                );
            });
        });
    }

    // ============================================================
    // GET FLASHCARD DETAIL
    // ============================================================

    @WithSession
    public Uni<FlashcardDetailResponse> getFlashcard(Long vocabularyId) {
        if (vocabularyId == null || vocabularyId <= 0) {
            throw new ValidationException(
                    "FLASHCARD_VALIDATION_ERROR",
                    "Invalid flashcard request",
                    List.of(new ValidationError("id", "Vocabulary ID must be greater than 0"))
            );
        }

        return flashcardRepository.findVocabularyById(vocabularyId)
                .onFailure(NoResultException.class)
                .transform(failure -> new ResourceNotFoundException(
                        "VOCABULARY_NOT_FOUND",
                        "Vocabulary " + vocabularyId + " not found"
                ))
                .flatMap(vocabulary ->
                        flashcardRepository.findReadings(vocabulary.id).flatMap(readings ->
                                flashcardRepository.findMeanings(vocabulary.id).flatMap(meanings ->
                                        flashcardRepository.findPartsOfSpeech(vocabulary.id).flatMap(partsOfSpeech ->
                                                flashcardRepository.findLevels(vocabulary.id).flatMap(levels ->
                                                        flashcardRepository.findLessons(vocabulary.id).flatMap(lessons ->
                                                                flashcardRepository.findKanji(vocabulary.id).flatMap(kanji ->
                                                                        flashcardRepository.findExamples(vocabulary.id).flatMap(examples ->
                                                                                buildDetailResponse(
                                                                                        vocabulary, readings, meanings, partsOfSpeech,
                                                                                        levels, lessons, kanji, examples
                                                                                )
                                                                        )))))))
                );
    }

    private Uni<FlashcardDetailResponse> buildDetailResponse(
            Vocabulary vocabulary,
            List<VocabularyReading> readings,
            List<VocabularyMeaning> meanings,
            List<PartOfSpeech> partsOfSpeech,
            List<JlptLevel> levels,
            List<Lesson> lessons,
            List<Kanji> kanji,
            List<VocabularyExample> examples
    ) {
        List<Long> readingIds = readings.stream().map(reading -> reading.id).toList();
        List<Long> kanjiIds = kanji.stream().map(character -> character.id).toList();

        return flashcardRepository.findPitchAccentsByReadingIds(readingIds)
                .flatMap(pitchAccents -> flashcardRepository.findKanjiReadings(kanjiIds)
                        .map(kanjiReadings -> {
                            Map<Long, List<Integer>> pitchAccentMap = pitchAccents.stream()
                                    .collect(Collectors.groupingBy(
                                            accent -> accent.vocabularyReadingId,
                                            Collectors.mapping(
                                                    accent -> accent.accentPattern,
                                                    Collectors.toList()
                                            )
                                    ));

                            Map<Long, List<FlashcardKanjiReadingResponse>> kanjiReadingMap =
                                    kanjiReadings.stream().collect(Collectors.groupingBy(
                                            reading -> reading.kanjiId,
                                            Collectors.mapping(
                                                    reading -> new FlashcardKanjiReadingResponse(
                                                            reading.reading, reading.readingType
                                                    ),
                                                    Collectors.toList()
                                            )
                                    ));

                            return new FlashcardDetailResponse(
                                    new VocabularyResponse(
                                            vocabulary.id, vocabulary.word, vocabulary.normalizedWord
                                    ),
                                    readings.stream().map(reading -> new FlashcardReadingResponse(
                                            reading.reading,
                                            reading.isPrimary,
                                            pitchAccentMap.getOrDefault(reading.id, List.of())
                                    )).toList(),
                                    meanings.stream().map(meaning -> new FlashcardMeaningResponse(
                                            meaning.languageCode, meaning.meaning, meaning.isPrimary
                                    )).toList(),
                                    partsOfSpeech.stream().map(pos -> new FlashcardPartOfSpeechResponse(
                                            pos.code, pos.nameVi, pos.nameEn
                                    )).toList(),
                                    levels.stream().map(level -> new FlashcardLevelResponse(
                                            level.code, level.name
                                    )).toList(),
                                    lessons.stream().map(lesson -> new FlashcardLessonResponse(
                                            lesson.level.code,
                                            lesson.level.name,
                                            lesson.lessonNumber,
                                            lesson.title,
                                            lesson.description,
                                            lesson.displayOrder
                                    )).toList(),
                                    kanji.stream().map(character -> new FlashcardKanjiResponse(
                                            character.character,
                                            character.strokeCount,
                                            character.meaningVi,
                                            character.meaningEn,
                                            kanjiReadingMap.getOrDefault(character.id, List.of())
                                    )).toList(),
                                    examples.stream().map(example -> new FlashcardExampleResponse(
                                            example.exampleSentence.japaneseText,
                                            example.exampleSentence.japaneseReading,
                                            example.exampleSentence.meaningVi,
                                            example.exampleSentence.meaningEn,
                                            example.targetText
                                    )).toList()
                            );
                        })
                );
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validateListRequest(String levelCode, Integer lesson, int page, int size) {
        List<ValidationError> errors = new ArrayList<>();

        if (!SUPPORTED_LEVELS.contains(levelCode)) {
            errors.add(new ValidationError("level", "Level must be one of N1, N2, N3, N4, N5"));
        }
        if (lesson != null && lesson <= 0) {
            errors.add(new ValidationError("lesson", "Lesson must be greater than 0"));
        }
        if (page < 0) {
            errors.add(new ValidationError("page", "Page must be greater than or equal to 0"));
        }
        if (size <= 0 || size > 100) {
            errors.add(new ValidationError("size", "Size must be between 1 and 100"));
        } else if (page >= 0 && (long) page * size > Integer.MAX_VALUE) {
            errors.add(new ValidationError("page", "Page exceeds the supported pagination range"));
        }

        if (!errors.isEmpty()) {
            throw new ValidationException(
                    "FLASHCARD_VALIDATION_ERROR",
                    "Invalid flashcard request",
                    errors
            );
        }
    }

    private String normalizeLevel(String level) {

        if (level == null || level.isBlank()) {
            return "N5";
        }

        return level.trim().toUpperCase(Locale.ROOT);
    }
}