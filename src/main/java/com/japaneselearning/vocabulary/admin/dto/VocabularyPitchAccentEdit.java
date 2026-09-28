package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record VocabularyPitchAccentEdit(
        @NotNull @Positive Long readingId,
        @NotNull @Min(0) @Max(65535) Integer accentPattern) {
}
