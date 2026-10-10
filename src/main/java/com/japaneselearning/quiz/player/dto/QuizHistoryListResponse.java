package com.japaneselearning.quiz.player.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description =
        "Zero-based completed-session history page in completion time and session ID"
                + " descending order"
)
public record QuizHistoryListResponse(
        List<QuizHistorySummary> items,
        int page,
        int size,
        long totalElements,
        long totalPages
) {
}
