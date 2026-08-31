package com.japaneselearning.flashcard.dto;

public record FlashcardExampleResponse(
        String japaneseText,
        String japaneseReading,
        String meaningVi,
        String meaningEn,
        String targetText
) {
}