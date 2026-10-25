package com.japaneselearning.flashcard.dto;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record LearningStatisticsResponse(
        long totalVocabularyCount,
        long newCount,
        long learningCount,
        long reviewCount,
        long masteredCount,
        long dueCount,
        long studiedCount,
        long totalReviewAttempts,
        long rememberedCount,
        long forgottenCount,
        long scheduledReviewAttempts,
        long practiceReviewAttempts,
        BigDecimal studiedPercentage,
        BigDecimal masteryPercentage
) {
    public static BigDecimal percentage(long count, long total) {
        return total == 0
                ? BigDecimal.ZERO.setScale(2)
                : BigDecimal.valueOf(count)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP);
    }
}
