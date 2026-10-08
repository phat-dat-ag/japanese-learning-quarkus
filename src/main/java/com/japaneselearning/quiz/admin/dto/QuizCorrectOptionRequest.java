package com.japaneselearning.quiz.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Admin Kanji Quiz request; see endpoint rules")
public record QuizCorrectOptionRequest(
        @NotNull
        @Min(0)
        Long version,

        @NotNull
        @Positive
        Long optionId
) {
}
