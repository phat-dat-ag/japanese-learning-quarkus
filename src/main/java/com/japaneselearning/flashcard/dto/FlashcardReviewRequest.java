package com.japaneselearning.flashcard.dto;

import com.japaneselearning.flashcard.domain.ReviewRating;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record FlashcardReviewRequest(
        @NotNull
        ReviewRating rating,

        @NotNull
        @PositiveOrZero
        Long expectedProgressVersion
) {
}
