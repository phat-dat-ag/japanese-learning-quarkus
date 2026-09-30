package com.japaneselearning.flashcard.dto;

public record FlashcardExampleResponse(
        Long exampleId,
        String japaneseText,
        String japaneseReading,
        String meaningVi,
        String meaningEn,
        String targetText,
        Integer displayOrder
) {
}