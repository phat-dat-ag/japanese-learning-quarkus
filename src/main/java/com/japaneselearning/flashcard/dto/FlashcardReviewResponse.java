package com.japaneselearning.flashcard.dto;

import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.domain.ReviewRating;

import java.time.Instant;

public record FlashcardReviewResponse(
        Long reviewId,
        Long sessionItemId,
        Long vocabularyId,
        ReviewRating rating,
        boolean scheduling,
        ProgressState progress,
        Instant reviewedAt
) {
}
