package com.japaneselearning.quiz.player.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuizProgressResponse(
        long completedSessions,
        long answeredCount,
        long correctCount,
        long incorrectCount,
        BigDecimal accuracyPercentage,
        long bestScore,
        BigDecimal averageScore,
        LocalDateTime latestCompletedAt
) {
}
