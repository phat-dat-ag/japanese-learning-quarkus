package com.japaneselearning.flashcard.dto;

import java.util.List;

public record FlashcardKanjiResponse(
        Long kanjiId,
        String character,
        Integer strokeCount,
        String meaningVi,
        String meaningEn,
        List<FlashcardKanjiReadingResponse> readings,
        Integer displayOrder
) {
}