package com.japaneselearning.quiz.admin.dto;

import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;

public record QuizQuestionFilter(
        QuestionSource sourceType,
        QuestionStatus status,
        Long levelId,
        Long lessonId,
        Long vocabularyId,
        Long exampleId,
        String keyword,
        int page,
        int size
) {
}
