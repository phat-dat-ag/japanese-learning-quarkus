package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.response.ErrorResponse;

public final class HttpErrors {
    private HttpErrors() {
    }

    public static ErrorResponse forStatus(int status) {
        return switch (status) {
            case 400 -> ErrorResponse.of("BAD_REQUEST", "Bad request");
            case 401 -> ErrorResponse.of("UNAUTHORIZED", "Authentication is required");
            case 403 -> ErrorResponse.of("FORBIDDEN", "Access is forbidden");
            case 404 -> ErrorResponse.of("NOT_FOUND", "Resource not found");
            case 405 -> ErrorResponse.of("METHOD_NOT_ALLOWED", "Method not allowed");
            case 409 -> ErrorResponse.of("CONFLICT", "The request conflicts with the current state");
            case 413 -> ErrorResponse.of("PAYLOAD_TOO_LARGE", "Request body is too large");
            case 415 -> ErrorResponse.of("UNSUPPORTED_MEDIA_TYPE", "Unsupported media type");
            case 429 -> ErrorResponse.of("TOO_MANY_REQUESTS", "Too many requests");
            default -> status >= 500
                    ? ErrorResponse.of("INTERNAL_SERVER_ERROR", "An unexpected error occurred")
                    : ErrorResponse.of("HTTP_ERROR", "HTTP request failed");
        };
    }
}
