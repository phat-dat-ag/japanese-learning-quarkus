package com.japaneselearning.quiz.player.dto;

import com.japaneselearning.quiz.domain.QuizSessionStatus;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Owned session progress; score is the number of correct submitted answers")
public record QuizSessionResponse(
        Long sessionId,
        QuizSessionStatus status,
        int questionCount,
        long answeredCount,
        long score
) {
}
