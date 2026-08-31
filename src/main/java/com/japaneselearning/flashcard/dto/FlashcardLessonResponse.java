package com.japaneselearning.flashcard.dto;

public record FlashcardLessonResponse(
        String levelCode,
        String levelName,
        Integer lessonNumber,
        String title,
        String description,
        Integer displayOrder
) {
}