package com.japaneselearning.flashcard.dto;

public record FlashcardLessonResponse(
        Long lessonId,
        String levelCode,
        String levelName,
        Integer lessonNumber,
        String title,
        String description,
        Integer displayOrder,
        Integer assignmentDisplayOrder
) {
}