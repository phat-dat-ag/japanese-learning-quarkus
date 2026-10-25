package com.japaneselearning.flashcard.dto;

import com.japaneselearning.flashcard.domain.FlashcardSessionMode;
import com.japaneselearning.flashcard.domain.FlashcardSessionStatus;
import com.japaneselearning.flashcard.domain.ProgressState;

import java.time.Instant;
import java.util.List;

public record FlashcardSessionResponse(
        Long sessionId,
        Long lessonId,
        FlashcardSessionMode mode,
        FlashcardSessionStatus status,
        int itemCount,
        int completedCount,
        int currentPosition,
        Instant createdAt,
        Instant completedAt,
        List<Item> items,
        Instant abandonedAt
) {
    public record Item(
            Long itemId,
            Long vocabularyId,
            int position,
            Instant completedAt,
            ProgressState progress
    ) {
    }
}
