package com.japaneselearning.quiz.admin.dto;

import java.util.List;

public record QuizQuestionListResponse(
        List<QuizQuestionResponse> items,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
}
