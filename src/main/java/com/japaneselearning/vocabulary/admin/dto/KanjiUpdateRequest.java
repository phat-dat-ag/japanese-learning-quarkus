package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record KanjiUpdateRequest(
        @NotBlank
        @Size(max = 10)
        String character,

        @Min(0)
        @Max(65535)
        Integer strokeCount,

        @Size(max = 500)
        String meaningVi,

        @Size(max = 500)
        String meaningEn,

        @NotNull
        @Min(0)
        Integer displayOrder
) {
}
