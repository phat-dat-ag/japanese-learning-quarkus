package com.japaneselearning.quiz.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.japaneselearning.quiz.domain.QuizSessionStatus;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Next unanswered immutable snapshot; reading does not advance the session")
public record QuizNextQuestionResponse(
        Long sessionId,
        QuizSessionStatus status,
        @JsonInclude(JsonInclude.Include.ALWAYS) Question question
) {
    @Schema(
            name = "QuizPlayerQuestion",
            description = "Safe session snapshot without answer keys or explanations"
    )
    public record Question(
            Long sessionQuestionId,
            int questionNumber,
            String sentenceReading,
            int targetStart,
            int targetLength,
            List<Option> options) {
    }

    @Schema(name = "QuizPlayerOption", description = "Previously shuffled snapshot option")
    public record Option(Long id, String text) {
    }
}
