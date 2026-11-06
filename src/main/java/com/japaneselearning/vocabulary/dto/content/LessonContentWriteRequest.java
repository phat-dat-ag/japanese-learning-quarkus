package com.japaneselearning.vocabulary.dto.content;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

@JsonDeserialize(using = LessonContentRequestDeserializer.class)
public record LessonContentWriteRequest(
        @NotNull
        @Positive
        Integer schemaVersion,

        @NotNull
        @PositiveOrZero
        Long expectedVersion,

        @NotNull
        LessonContentDocument content
) {
}
