package com.japaneselearning.quiz.player.dto;

import com.japaneselearning.quiz.domain.QuizSessionStatus;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema(description = "Committed session metadata only; no question content or correct answers")
public record QuizSessionCreatedResponse(
        Long sessionId,
        QuizSessionStatus status,
        int questionCount,
        LocalDateTime createdAt
) {
}
