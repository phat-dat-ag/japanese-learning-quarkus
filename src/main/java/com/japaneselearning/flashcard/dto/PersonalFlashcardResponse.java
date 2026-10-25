package com.japaneselearning.flashcard.dto;

import com.japaneselearning.flashcard.domain.ProgressState;

public record PersonalFlashcardResponse(
        Long id,
        String word,
        ProgressState progress,
        boolean bookmarked
) {
}
