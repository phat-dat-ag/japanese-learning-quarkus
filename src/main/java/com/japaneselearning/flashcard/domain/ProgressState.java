package com.japaneselearning.flashcard.domain;

import java.time.Instant;

public record ProgressState(
        LearningStatus status,
        long successfulRepetitionCount,
        long lapseCount,
        long intervalSeconds,
        double easeFactor,
        Instant lastReviewedAt,
        Instant nextReviewAt,
        Instant masteredAt,
        long version
) {
    public static ProgressState unseen() {
        return new ProgressState(LearningStatus.NEW, 0, 0, 0, 2.5, null, null, null, 0);
    }
}
