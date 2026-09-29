package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LessonAssignmentAddRequest(
        @NotNull
        @Positive
        Long lessonId,

        @NotNull
        @Min(1)
        Integer displayOrder
) {
}
