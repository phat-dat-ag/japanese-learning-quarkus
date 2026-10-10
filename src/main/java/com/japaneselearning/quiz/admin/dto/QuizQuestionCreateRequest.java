package com.japaneselearning.quiz.admin.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Admin Kanji Quiz request; see endpoint rules")
public record QuizQuestionCreateRequest(
        @NotNull
        @Valid
        QuizQuestionContentRequest content,

        @NotNull
        @Size(min = 4, max = 4)
        List<@NotNull @Valid QuizOptionRequest> options
) {
}
