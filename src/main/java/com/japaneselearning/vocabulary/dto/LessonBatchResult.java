package com.japaneselearning.vocabulary.dto;

import com.japaneselearning.common.response.ErrorResponse;

public record LessonBatchResult(
        int index,
        boolean success,
        LessonResponse lesson,
        ErrorResponse error
) {
}
