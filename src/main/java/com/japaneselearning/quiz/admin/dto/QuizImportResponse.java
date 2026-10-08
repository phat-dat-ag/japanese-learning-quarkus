package com.japaneselearning.quiz.admin.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Accepted batch summary: total equals imported plus failed; results follow input order")
public record QuizImportResponse(
        int total,
        int imported,
        int failed,
        List<QuizImportResult> results
) {
    public static QuizImportResponse from(List<QuizImportResult> results) {
        int imported = (int) results.stream().filter(
                result -> result.status() == QuizImportStatus.IMPORTED
        ).count();

        return new QuizImportResponse(
                results.size(),
                imported,
                results.size() - imported,
                List.copyOf(results)
        );
    }
}
