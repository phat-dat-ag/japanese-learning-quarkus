package com.japaneselearning.flashcard.dto;

public record FlashcardMeaningResponse(
        String languageCode,
        String meaning,
        Boolean isPrimary
) {
}