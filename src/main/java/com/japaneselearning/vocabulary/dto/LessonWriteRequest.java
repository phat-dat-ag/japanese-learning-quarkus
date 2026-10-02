package com.japaneselearning.vocabulary.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record LessonWriteRequest(
        @NotNull
        @Positive
        Long levelId,

        @NotNull
        @Positive
        Integer lessonNumber,

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 1000)
        String description,

        @NotNull
        @Positive
        Integer displayOrder
) {
}
