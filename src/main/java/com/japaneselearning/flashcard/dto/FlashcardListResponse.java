package com.japaneselearning.flashcard.dto;

import java.util.List;

public record FlashcardListResponse(
        List<FlashcardListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}