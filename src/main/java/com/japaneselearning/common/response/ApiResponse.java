package com.japaneselearning.common.response;

public record ApiResponse<T>(
        boolean success,
        T data,
        ErrorResponse error,
        ResponseMeta meta
) {

    public static <T> ApiResponse<T> success(
            T data,
            ResponseMeta responseMeta
    ) {
        return new ApiResponse<>(
                true,
                data,
                null,
                responseMeta
        );
    }

    public static <T> ApiResponse<T> error(
            ErrorResponse error,
            ResponseMeta responseMeta
    ) {
        return new ApiResponse<>(
                false,
                null,
                error,
                responseMeta
        );
    }
}