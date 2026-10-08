package com.japaneselearning.quiz.admin.dto;

public record QuizOptionResponse(
        Long id,
        String text,
        boolean correct
) {
}
