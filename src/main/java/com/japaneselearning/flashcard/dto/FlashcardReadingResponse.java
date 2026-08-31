package com.japaneselearning.flashcard.dto;

import java.util.List;

public record FlashcardReadingResponse(
        String reading,
        Boolean isPrimary,
        List<Integer> pitchAccents
) {
}