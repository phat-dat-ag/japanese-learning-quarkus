package com.japaneselearning.vocabulary.admin.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record AssignmentOrderEdit(
        @NotNull @Min(0) Integer displayOrder) {
}
