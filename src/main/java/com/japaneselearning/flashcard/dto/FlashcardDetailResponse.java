package com.japaneselearning.flashcard.dto;

import java.util.List;

public record FlashcardDetailResponse(
        VocabularyResponse vocabulary,
        List<FlashcardReadingResponse> readings,
        List<FlashcardMeaningResponse> meanings,
        List<FlashcardPartOfSpeechResponse> partsOfSpeech,
        List<FlashcardLevelResponse> levels,
        List<FlashcardLessonResponse> lessons,
        List<FlashcardKanjiResponse> kanji,
        List<FlashcardExampleResponse> examples
) {
}