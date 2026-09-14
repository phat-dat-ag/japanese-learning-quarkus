package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.FlashcardListItemResponse;
import com.japaneselearning.flashcard.dto.FlashcardListResponse;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ApplicationScoped
public class FlashcardService {

    private static final Set<String> SUPPORTED_LEVELS = Set.of("N1", "N2", "N3", "N4", "N5");

    private final FlashcardRepository flashcardRepository;

    public FlashcardService(FlashcardRepository flashcardRepository) {
        this.flashcardRepository = flashcardRepository;
    }

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
                .map(FlashcardDetailAssembler::new)
                .call(detail -> flashcardRepository.findReadings(detail.vocabularyId())
                        .invoke(detail::setReadings))
                .call(detail -> flashcardRepository.findMeanings(detail.vocabularyId())
                        .invoke(detail::setMeanings))
                .call(detail -> flashcardRepository.findPartsOfSpeech(detail.vocabularyId())
                        .invoke(detail::setPartsOfSpeech))
                .call(detail -> flashcardRepository.findLevels(detail.vocabularyId())
                        .invoke(detail::setLevels))
                .call(detail -> flashcardRepository.findLessons(detail.vocabularyId())
                        .invoke(detail::setLessons))
                .call(detail -> flashcardRepository.findKanji(detail.vocabularyId())
                        .invoke(detail::setKanji))
                .call(detail -> flashcardRepository.findExamples(detail.vocabularyId())
                        .invoke(detail::setExamples))
                .call(detail -> flashcardRepository.findPitchAccentsByReadingIds(detail.readingIds())
                        .invoke(detail::setPitchAccents))
                .call(detail -> flashcardRepository.findKanjiReadings(detail.kanjiIds())
                        .invoke(detail::setKanjiReadings))
                .map(FlashcardDetailAssembler::build);
    }

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
