package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record VocabularyCoreEdit(
        @NotBlank @Size(max = 100) String word,
        @NotBlank @Size(max = 100) String normalizedWord) {
}
