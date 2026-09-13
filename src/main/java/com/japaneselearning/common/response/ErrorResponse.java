package com.japaneselearning.common.response;

import com.japaneselearning.common.exception.ValidationError;

import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        List<ValidationError> details
) {

    public static ErrorResponse of(
            String code,
            String message
    ) {
        return new ErrorResponse(
                code,
                message,
                List.of()
        );
    }

    public static ErrorResponse validation(
            String code,
            String message,
            List<ValidationError> details
    ) {
        return new ErrorResponse(
                code,
                message,
                details
        );
    }
}