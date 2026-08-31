package com.japaneselearning.flashcard.service;

import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
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
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class FlashcardService {

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

        validatePagination(page, size);

        validateLesson(lesson);

        String levelCode = normalizeLevel(level);

        int offset = page * size;

        Uni<List<Object[]>> vocabularyUni;

        Uni<Long> countUni;

        if (lesson == null) {

            vocabularyUni =
                    flashcardRepository.findVocabularyByLevel(
                            levelCode,
                            offset,
                            size
                    );

            countUni =
                    flashcardRepository.countVocabularyByLevel(
                            levelCode
                    );

        } else {

            vocabularyUni =
                    flashcardRepository.findVocabularyByLevelAndLesson(
                            levelCode,
                            lesson,
                            offset,
                            size
                    );

            countUni =
                    flashcardRepository.countVocabularyByLevelAndLesson(
                            levelCode,
                            lesson
                    );
        }

        return Uni.combine()
                .all()
                .unis(vocabularyUni, countUni)
                .asTuple()
                .map(tuple -> {

                    List<Object[]> rows = tuple.getItem1();
                    Long totalElements = tuple.getItem2();

                    List<FlashcardListItemResponse> items =
                            rows.stream()
                                    .map(row ->
                                            new FlashcardListItemResponse(
                                                    ((Number) row[0]).longValue(),
                                                    (String) row[1]
                                            )
                                    )
                                    .toList();

                    int totalPages =
                            (int) Math.ceil(
                                    (double) totalElements / size
                            );

                    return new FlashcardListResponse(
                            items,
                            page,
                            size,
                            totalElements,
                            totalPages
                    );
                });
    }

    // ============================================================
    // GET FLASHCARD DETAIL
    // ============================================================

    @WithSession
    public Uni<FlashcardDetailResponse> getFlashcard(
            Long vocabularyId
    ) {

        return flashcardRepository
                .findVocabularyById(vocabularyId)
                .flatMap(vocabulary -> {

                    Long id =
                            ((Number) vocabulary[0]).longValue();

                    String word =
                            (String) vocabulary[1];

                    return Uni.combine()
                            .all()
                            .unis(
                                    flashcardRepository.findReadings(id),
                                    flashcardRepository.findMeanings(id),
                                    flashcardRepository.findPartsOfSpeech(id),
                                    flashcardRepository.findLevels(id),
                                    flashcardRepository.findLessons(id),
                                    flashcardRepository.findKanji(id),
                                    flashcardRepository.findExamples(id)
                            )
                            .asTuple()
                            .flatMap(tuple -> {

                                List<Object[]> readings =
                                        tuple.getItem1();

                                List<Object[]> meanings =
                                        tuple.getItem2();

                                List<Object[]> partsOfSpeech =
                                        tuple.getItem3();

                                List<Object[]> levels =
                                        tuple.getItem4();

                                List<Object[]> lessons =
                                        tuple.getItem5();

                                List<Object[]> kanji =
                                        tuple.getItem6();

                                List<Object[]> examples =
                                        tuple.getItem7();

                                return buildDetailResponse(
                                        id,
                                        word,
                                        readings,
                                        meanings,
                                        partsOfSpeech,
                                        levels,
                                        lessons,
                                        kanji,
                                        examples
                                );
                            });
                });
    }

    // ============================================================
    // BUILD DETAIL RESPONSE
    // ============================================================

    private Uni<FlashcardDetailResponse> buildDetailResponse(
            Long id,
            String word,
            List<Object[]> readings,
            List<Object[]> meanings,
            List<Object[]> partsOfSpeech,
            List<Object[]> levels,
            List<Object[]> lessons,
            List<Object[]> kanji,
            List<Object[]> examples
    ) {

        // --------------------------------------------------------
        // Reading IDs
        // --------------------------------------------------------

        List<Long> readingIds =
                readings.stream()
                        .map(row ->
                                ((Number) row[0]).longValue()
                        )
                        .toList();

        // --------------------------------------------------------
        // Kanji IDs
        // --------------------------------------------------------

        List<Long> kanjiIds =
                kanji.stream()
                        .map(row ->
                                ((Number) row[0]).longValue()
                        )
                        .toList();

        // --------------------------------------------------------
        // Batch load pitch accents + kanji readings
        // --------------------------------------------------------

        Uni<List<Object[]>> pitchAccentsUni =
                flashcardRepository
                        .findPitchAccentsByReadingIds(
                                readingIds
                        );

        Uni<List<Object[]>> kanjiReadingsUni =
                flashcardRepository
                        .findKanjiReadings(
                                kanjiIds
                        );

        return Uni.combine()
                .all()
                .unis(
                        pitchAccentsUni,
                        kanjiReadingsUni
                )
                .asTuple()
                .map(tuple -> {

                    List<Object[]> pitchAccentRows =
                            tuple.getItem1();

                    List<Object[]> kanjiReadingRows =
                            tuple.getItem2();

                    // ====================================================
                    // PITCH ACCENT MAP
                    // readingId -> List<Integer>
                    // ====================================================

                    Map<Long, List<Integer>> pitchAccentMap =
                            pitchAccentRows.stream()
                                    .collect(
                                            Collectors.groupingBy(
                                                    row ->
                                                            ((Number) row[0])
                                                                    .longValue(),

                                                    Collectors.mapping(
                                                            row ->
                                                                    ((Number) row[1])
                                                                            .intValue(),

                                                            Collectors.toList()
                                                    )
                                            )
                                    );

                    // ====================================================
                    // KANJI READING MAP
                    // kanjiId -> List<FlashcardKanjiReadingResponse>
                    // ====================================================

                    Map<Long,
                            List<FlashcardKanjiReadingResponse>>
                            kanjiReadingMap =
                            kanjiReadingRows.stream()
                                    .collect(
                                            Collectors.groupingBy(
                                                    row ->
                                                            ((Number) row[0])
                                                                    .longValue(),

                                                    Collectors.mapping(
                                                            row ->
                                                                    new FlashcardKanjiReadingResponse(
                                                                            (String) row[1],
                                                                            (String) row[2]
                                                                    ),

                                                            Collectors.toList()
                                                    )
                                            )
                                    );

                    // ====================================================
                    // READINGS
                    // ====================================================

                    List<FlashcardReadingResponse>
                            readingResponses =
                            readings.stream()
                                    .map(row -> {

                                        Long readingId =
                                                ((Number) row[0])
                                                        .longValue();

                                        String reading =
                                                (String) row[1];

                                        Boolean isPrimary =
                                                (Boolean) row[2];

                                        List<Integer> pitchAccents =
                                                pitchAccentMap.getOrDefault(
                                                        readingId,
                                                        List.of()
                                                );

                                        return new FlashcardReadingResponse(
                                                reading,
                                                isPrimary,
                                                pitchAccents
                                        );
                                    })
                                    .toList();

                    // ====================================================
                    // MEANINGS
                    // ====================================================

                    List<FlashcardMeaningResponse>
                            meaningResponses =
                            meanings.stream()
                                    .map(row ->
                                            new FlashcardMeaningResponse(
                                                    (String) row[0],
                                                    (String) row[1],
                                                    (Boolean) row[2]
                                            )
                                    )
                                    .toList();

                    // ====================================================
                    // PARTS OF SPEECH
                    // ====================================================

                    List<FlashcardPartOfSpeechResponse>
                            posResponses =
                            partsOfSpeech.stream()
                                    .map(row ->
                                            new FlashcardPartOfSpeechResponse(
                                                    (String) row[0],
                                                    (String) row[1],
                                                    (String) row[2]
                                            )
                                    )
                                    .toList();

                    // ====================================================
                    // JLPT LEVELS
                    // ====================================================

                    List<FlashcardLevelResponse>
                            levelResponses =
                            levels.stream()
                                    .map(row ->
                                            new FlashcardLevelResponse(
                                                    (String) row[0],
                                                    (String) row[1]
                                            )
                                    )
                                    .toList();

                    // ====================================================
                    // LESSONS
                    // ====================================================

                    List<FlashcardLessonResponse>
                            lessonResponses =
                            lessons.stream()
                                    .map(row ->
                                            new FlashcardLessonResponse(
                                                    (String) row[0],
                                                    (String) row[1],
                                                    ((Number) row[2]).intValue(),
                                                    (String) row[3],
                                                    (String) row[4],
                                                    ((Number) row[5]).intValue()
                                            )
                                    )
                                    .toList();

                    // ====================================================
                    // KANJI
                    // ====================================================

                    List<FlashcardKanjiResponse>
                            kanjiResponses =
                            kanji.stream()
                                    .map(row -> {

                                        Long kanjiId =
                                                ((Number) row[0])
                                                        .longValue();

                                        String character =
                                                (String) row[1];

                                        Integer strokeCount =
                                                row[2] == null
                                                        ? null
                                                        : ((Number) row[2])
                                                        .intValue();

                                        String meaningVi =
                                                (String) row[3];

                                        String meaningEn =
                                                (String) row[4];

                                        List<FlashcardKanjiReadingResponse>
                                                kanjiReadings =
                                                kanjiReadingMap.getOrDefault(
                                                        kanjiId,
                                                        List.of()
                                                );

                                        return new FlashcardKanjiResponse(
                                                character,
                                                strokeCount,
                                                meaningVi,
                                                meaningEn,
                                                kanjiReadings
                                        );
                                    })
                                    .toList();

                    // ====================================================
                    // EXAMPLES
                    // ====================================================

                    List<FlashcardExampleResponse>
                            exampleResponses =
                            examples.stream()
                                    .map(row ->
                                            new FlashcardExampleResponse(
                                                    (String) row[0],
                                                    (String) row[1],
                                                    (String) row[2],
                                                    (String) row[3],
                                                    (String) row[4]
                                            )
                                    )
                                    .toList();

                    // ====================================================
                    // FINAL RESPONSE
                    // ====================================================

                    return new FlashcardDetailResponse(
                            id,
                            word,
                            readingResponses,
                            meaningResponses,
                            posResponses,
                            levelResponses,
                            lessonResponses,
                            kanjiResponses,
                            exampleResponses
                    );
                });
    }

    // ============================================================
    // VALIDATION
    // ============================================================

    private void validatePagination(
            int page,
            int size
    ) {

        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page must be greater than or equal to 0"
            );
        }

        if (size <= 0 || size > 100) {
            throw new IllegalArgumentException(
                    "Size must be between 1 and 100"
            );
        }
    }

    private void validateLesson(Integer lesson) {

        if (lesson != null && lesson <= 0) {
            throw new IllegalArgumentException(
                    "Lesson must be greater than 0"
            );
        }
    }

    private String normalizeLevel(String level) {

        if (level == null || level.isBlank()) {
            return "N5";
        }

        return level.trim().toUpperCase();
    }
}