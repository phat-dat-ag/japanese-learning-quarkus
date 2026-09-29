package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VocabularyExampleUpdateRequest(
        @NotBlank
        @Size(max = 1000)
        String japaneseText,

        @NotBlank
        @Size(max = 1000)
        String japaneseReading,

        @NotBlank
        @Size(max = 1000)
        String meaningVi,

        @NotBlank
        @Size(max = 1000)
        String meaningEn,

        @NotBlank
        @Size(max = 200)
        String targetText,

        @NotNull
        @Min(0)
        Integer displayOrder
) {
}
