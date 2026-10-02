package com.japaneselearning.vocabulary.dto;

import java.util.List;

public record LessonBatchResponse(
        int total,
        int succeeded,
        int failed,
        List<LessonBatchResult> results
) {
    public static LessonBatchResponse from(List<LessonBatchResult> results) {
        int succeeded = (int) results.stream().filter(LessonBatchResult::success).count();

        return new LessonBatchResponse(
                results.size(),
                succeeded,
                results.size() - succeeded,
                List.copyOf(results)
        );
    }
}
