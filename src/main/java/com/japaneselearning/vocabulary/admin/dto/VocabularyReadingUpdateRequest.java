package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record VocabularyReadingUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String reading,

        @NotNull
        Boolean isPrimary,

        @NotNull
        @Min(0)
        Integer displayOrder
) {
}
