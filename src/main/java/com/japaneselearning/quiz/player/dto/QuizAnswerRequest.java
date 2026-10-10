package com.japaneselearning.quiz.player.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description =
        "Snapshot IDs from the current session question; "
                + "identity comes only from JWT sub"
)
public record QuizAnswerRequest(
        @NotNull
        @Positive
        Long sessionQuestionId,

        @NotNull
        @Positive
        Long selectedOptionId
) {
}
