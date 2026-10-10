package com.japaneselearning.quiz.admin.dto;

import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;

import java.time.LocalDateTime;
import java.util.List;

public record QuizQuestionResponse(
        Long id,
        long version,
        QuestionSource sourceType,
        QuestionStatus status,
        Long exampleId,
        Long vocabularyId,
        String sentenceReading,
        int targetStart,
        int targetLength,
        String targetReading,
        String explanationVi,
        String explanationEn,
        boolean sourceInvalidated,
        boolean eligibleForNewGames,
        List<Long> levelIds,
        List<Long> lessonIds,
        List<QuizOptionResponse> options,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
