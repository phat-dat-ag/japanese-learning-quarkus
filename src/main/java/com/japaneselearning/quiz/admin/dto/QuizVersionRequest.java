package com.japaneselearning.quiz.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Admin Kanji Quiz request; see endpoint rules")
public record QuizVersionRequest(
        @NotNull
        @Min(0)
        Long version
) {
}
