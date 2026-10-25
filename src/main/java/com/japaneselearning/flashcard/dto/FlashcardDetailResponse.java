package com.japaneselearning.flashcard.dto;

import com.japaneselearning.flashcard.domain.ProgressState;

import java.util.List;

public record FlashcardDetailResponse(
        VocabularyResponse vocabulary,
        List<FlashcardReadingResponse> readings,
        List<FlashcardMeaningResponse> meanings,
        List<FlashcardPartOfSpeechResponse> partsOfSpeech,
        List<FlashcardLevelResponse> levels,
        List<FlashcardLessonResponse> lessons,
        List<FlashcardKanjiResponse> kanji,
        List<FlashcardExampleResponse> examples,
        ProgressState progress,
        Boolean bookmarked
) {
    public FlashcardDetailResponse(
            VocabularyResponse vocabulary,
            List<FlashcardReadingResponse> readings,
            List<FlashcardMeaningResponse> meanings,
            List<FlashcardPartOfSpeechResponse> partsOfSpeech,
            List<FlashcardLevelResponse> levels,
            List<FlashcardLessonResponse> lessons,
            List<FlashcardKanjiResponse> kanji,
            List<FlashcardExampleResponse> examples
    ) {
        this(
                vocabulary,
                readings,
                meanings,
                partsOfSpeech,
                levels,
                lessons,
                kanji,
                examples,
                null,
                null
        );
    }
}