package com.japaneselearning.vocabulary.importer.dto;

public record ImportResult(
        int total,
        int created,
        int updated
) {
}