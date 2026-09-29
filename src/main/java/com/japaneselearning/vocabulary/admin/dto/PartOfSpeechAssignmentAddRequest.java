package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartOfSpeechAssignmentAddRequest(
        @NotBlank
        @Size(max = 50)
        String code
) {
}
