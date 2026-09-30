package com.japaneselearning.flashcard.dto;

public record FlashcardKanjiReadingResponse(
        Long kanjiReadingId,
        String reading,
        String readingType,
        Integer displayOrder
) {
}