package com.japaneselearning.flashcard.dto;

import com.japaneselearning.flashcard.domain.ProgressState;

public record FlashcardListItemResponse(
        Long id,
        String word,
        ProgressState progress,
        Boolean bookmarked
) {
    public FlashcardListItemResponse(Long id, String word) {
        this(id, word, null, null);
    }
}