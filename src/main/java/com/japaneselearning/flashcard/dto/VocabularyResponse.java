package com.japaneselearning.flashcard.dto;

public record VocabularyResponse(
        Long id,
        String word,
        String normalizedWord
) {
}
