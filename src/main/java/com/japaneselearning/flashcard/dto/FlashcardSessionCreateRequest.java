package com.japaneselearning.flashcard.dto;

import com.japaneselearning.flashcard.domain.FlashcardSessionMode;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record FlashcardSessionCreateRequest(
        @NotNull @Positive
        Long lessonId,

        FlashcardSessionMode mode,

        @Min(1)
        @Max(100)
        Integer cardLimit,

        boolean shuffle
) {
    public FlashcardSessionMode selectedMode() {
        return mode == null ? FlashcardSessionMode.NEW_AND_DUE : mode;
    }

    public int selectedLimit() {
        return cardLimit == null ? 20 : cardLimit;
    }
}
