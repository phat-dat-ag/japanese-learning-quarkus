package com.japaneselearning.flashcard.dto;

import java.util.List;

public record FlashcardKanjiResponse(
        String character,
        Integer strokeCount,
        String meaningVi,
        String meaningEn,
        List<FlashcardKanjiReadingResponse> readings
) {
}