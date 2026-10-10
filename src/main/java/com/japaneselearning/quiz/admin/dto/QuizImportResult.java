package com.japaneselearning.quiz.admin.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Ordered item outcome: IDs/version for IMPORTED, errors for FAILED")
public record QuizImportResult(
        int index,
        QuizImportStatus status,
        Long questionId,
        Long version,
        List<QuizImportError> errors
) {
    public static QuizImportResult imported(int index, QuizQuestionResponse question) {
        return new QuizImportResult(
                index,
                QuizImportStatus.IMPORTED,
                question.id(),
                question.version(),
                null
        );
    }

    public static QuizImportResult failed(int index, List<QuizImportError> errors) {
        return new QuizImportResult(
                index,
                QuizImportStatus.FAILED,
                null,
                null,
                List.copyOf(errors)
        );
    }
}
