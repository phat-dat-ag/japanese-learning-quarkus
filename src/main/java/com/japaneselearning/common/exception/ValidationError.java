package com.japaneselearning.common.exception;

public record ValidationError(
        String field,
        String message
) {
}