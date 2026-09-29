package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record LevelAssignmentAddRequest(
        @NotBlank
        @Size(max = 10)
        String level,

        @NotNull
        @Min(0)
        Integer displayOrder
) {
}
