package com.japaneselearning.vocabulary.dto.content;

import com.japaneselearning.common.response.ErrorResponse;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

public record LessonContentImportResponse(
        int total,
        int created,
        int updated,
        int skipped,
        int failed,
        List<Item> results
) {
    public enum Mode {SKIP_EXISTING, UPDATE_EXISTING}

    public enum Status {CREATED, UPDATED, SKIPPED, FAILED}

    @Schema(name = "LessonContentImportResult")
    public record Item(
            int index,
            String level,
            Integer lessonNumber,
            Status status,
            Long lessonId,
            ErrorResponse error
    ) {
    }

    public static LessonContentImportResponse from(List<Item> items) {
        return new LessonContentImportResponse(
                items.size(),
                count(items, Status.CREATED),
                count(items, Status.UPDATED),
                count(items, Status.SKIPPED),
                count(items, Status.FAILED),
                List.copyOf(items)
        );
    }

    private static int count(List<Item> items, Status status) {
        return (int) items.stream().filter(item -> item.status() == status).count();
    }
}
