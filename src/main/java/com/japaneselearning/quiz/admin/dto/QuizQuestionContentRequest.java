package com.japaneselearning.quiz.admin.dto;

import com.japaneselearning.quiz.domain.QuestionSource;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Admin Kanji Quiz request; see endpoint rules")
public record QuizQuestionContentRequest(
        @NotNull
        QuestionSource sourceType,

        @Positive
        Long exampleId,

        @Positive
        Long vocabularyId,

        @Schema(description = "CUSTOM only; EXAMPLE always uses the source reading")
        String sentenceReading,

        @NotNull
        @Min(0)
        Integer targetStart,

        @NotNull
        @Positive
        Integer targetLength,

        @NotBlank
        String targetReading,

        String explanationVi,
        String explanationEn,

        @NotNull
        @Size(max = 100)
        List<@NotNull @Positive Long> levelIds,

        @NotNull
        @Size(max = 100)
        List<@NotNull @Positive Long> lessonIds
) {
}
