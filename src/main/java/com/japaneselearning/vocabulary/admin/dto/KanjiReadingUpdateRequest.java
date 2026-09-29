package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KanjiReadingUpdateRequest(
        @NotBlank
        @Size(max = 100)
        String reading,

        @NotBlank
        @Size(max = 20)
        String readingType,

        @NotNull
        @Min(0)
        Integer displayOrder
) {
}
