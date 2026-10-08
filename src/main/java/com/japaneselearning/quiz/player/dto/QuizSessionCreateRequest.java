package com.japaneselearning.quiz.player.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description = "Optional intersecting level/lesson filters and required count; identity comes only from JWT sub")
public record QuizSessionCreateRequest(
        @Positive
        Long levelId,

        @Positive
        Long lessonId,

        @NotNull
        @Min(1)
        @Max(100)
        Integer questionCount
) {
}
