package com.japaneselearning.quiz.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.japaneselearning.quiz.domain.QuizSessionStatus;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@JsonInclude(JsonInclude.Include.ALWAYS)
@Schema(description =
        "Completed session totals; selection IDs are null because existing sessions do not"
                + " retain creation filters"
)
public record QuizHistorySummary(
        Long sessionId,
        QuizSessionStatus status,
        Long levelId,
        Long lessonId,
        LocalDateTime completedAt,
        int questionCount,
        long correctCount,
        long incorrectCount,
        long score
) {
}
