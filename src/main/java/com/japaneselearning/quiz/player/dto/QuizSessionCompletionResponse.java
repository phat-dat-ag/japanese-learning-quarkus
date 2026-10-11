package com.japaneselearning.quiz.player.dto;

import com.japaneselearning.quiz.domain.QuizSessionStatus;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(
        description =
                "Completed session totals; "
                        + "questionCount is the total and score equals correctCount"
)
public record QuizSessionCompletionResponse(
        Long sessionId,
        QuizSessionStatus status,
        int questionCount,
        long correctCount,
        long incorrectCount,
        long score,
        LocalDateTime completedAt
) {
}
