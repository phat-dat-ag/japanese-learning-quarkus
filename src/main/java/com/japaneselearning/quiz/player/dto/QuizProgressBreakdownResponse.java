package com.japaneselearning.quiz.player.dto;

import java.math.BigDecimal;
import java.util.List;

public record QuizProgressBreakdownResponse(List<Bucket> levels, List<Bucket> lessons) {
    public enum Classification {
        ASSIGNED,
        UNASSIGNED,
        UNKNOWN
    }

    public record Bucket(
            Classification classification,
            Long levelId,
            Long lessonId,
            long completedSessions,
            long answeredCount,
            long correctCount,
            long incorrectCount,
            BigDecimal accuracyPercentage
    ) {
    }
}
