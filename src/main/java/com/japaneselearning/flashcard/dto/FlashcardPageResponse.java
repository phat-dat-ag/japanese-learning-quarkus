package com.japaneselearning.flashcard.dto;

import java.util.List;

public record FlashcardPageResponse<T>(
        List<T> items,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
    public static <T> FlashcardPageResponse<T> of(
            List<T> items,
            int page,
            int size,
            long total
    ) {
        return new FlashcardPageResponse<>(
                items,
                page,
                size,
                total,
                total / size + (total % size == 0 ? 0 : 1)
        );
    }
}
