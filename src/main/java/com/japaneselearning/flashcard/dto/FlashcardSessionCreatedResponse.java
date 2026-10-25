package com.japaneselearning.flashcard.dto;

public record FlashcardSessionCreatedResponse(
        String outcome,
        int eligibleCount,
        FlashcardSessionResponse session
) {
}
