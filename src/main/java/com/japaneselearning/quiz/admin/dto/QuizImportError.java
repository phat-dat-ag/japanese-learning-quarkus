package com.japaneselearning.quiz.admin.dto;

public record QuizImportError(
        String field,
        String code,
        String message
) {
}
