package com.japaneselearning.flashcard.dto;

import java.util.List;

public record FlashcardReadingResponse(
        Long readingId,
        String reading,
        Boolean isPrimary,
        Integer displayOrder,
        List<Integer> pitchAccents,
        List<FlashcardPitchAccentResponse> pitchAccentDetails
) {
}