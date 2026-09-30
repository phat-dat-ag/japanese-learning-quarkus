package com.japaneselearning.flashcard.dto;

public record FlashcardMeaningResponse(
        Long meaningId,
        String languageCode,
        String meaning,
        Boolean isPrimary,
        Integer displayOrder
) {
}