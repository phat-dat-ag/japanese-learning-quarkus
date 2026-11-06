package com.japaneselearning.vocabulary.dto.content;

import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record LessonContentImportItem(
        @NotBlank @Size(max = 10)
        String level,

        @NotNull
        @Positive
        Integer lessonNumber,

        @NotBlank
        @Size(max = 200)
        String title,

        @Size(max = 1000)
        String description,

        @NotNull
        @Positive
        Integer displayOrder,

        @NotNull
        @Positive
        Integer schemaVersion,

        @NotNull
        @PositiveOrZero
        Long expectedVersion,

        @Size(max = 200)
        String etag,

        @NotNull
        LessonContentDocument content
) {
    public LessonWriteRequest metadata(Long levelId) {
        return new LessonWriteRequest(
                levelId,
                lessonNumber,
                title,
                description,
                displayOrder
        );
    }

    public LessonContentWriteRequest document() {
        return new LessonContentWriteRequest(
                schemaVersion,
                expectedVersion,
                content
        );
    }
}
