package com.japaneselearning.common.exception;

import java.util.List;

public class ValidationException extends BusinessException {

    private final List<ValidationError> errors;

    public ValidationException(
            String code,
            String message,
            List<ValidationError> errors
    ) {
        super(code, message);
        this.errors = errors;
    }

    public List<ValidationError> getErrors() {
        return errors;
    }
}