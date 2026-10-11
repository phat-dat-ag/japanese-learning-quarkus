package com.japaneselearning.quiz.admin.service;

import com.japaneselearning.quiz.admin.dto.QuizImportError;
import com.japaneselearning.quiz.admin.dto.QuizQuestionCreateRequest;

import java.util.List;

// Prepared on the parsing worker before any database work, including for invalid objects.
public record QuizImportItem(
        QuizQuestionCreateRequest request,
        List<QuizImportError> errors
) {
    public QuizImportItem {
        errors = List.copyOf(errors);
    }
}
