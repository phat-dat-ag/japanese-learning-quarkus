package com.japaneselearning.vocabulary.dto;

public record LessonResponse(
        Long id,
        Integer lessonNumber,
        String title,
        String description
) {
}