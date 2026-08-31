package com.japaneselearning.common.exception;

public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(
            String code,
            String message
    ) {
        super(code, message);
    }
}