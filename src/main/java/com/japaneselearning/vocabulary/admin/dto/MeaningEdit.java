package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MeaningEdit(
        @NotNull @Pattern(regexp = "vi|en") String language,
        @NotBlank @Size(max = 500) String meaning,
        @NotNull Boolean isPrimary,
        @NotNull @Min(0) Integer displayOrder) {
}
