package com.japaneselearning.flashcard.dto;

public record FlashcardLevelResponse(
        Long levelId,
        String code,
        String name,
        Integer displayOrder
) {
}