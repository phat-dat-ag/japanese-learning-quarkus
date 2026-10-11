package com.japaneselearning.quiz.player.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.japaneselearning.quiz.player.dto.QuizNextQuestionResponse.Option;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "Owned completed session and immutable ordered question/answer history")
public record QuizHistoryDetailResponse(QuizHistorySummary session, List<Question> questions) {
    @JsonInclude(JsonInclude.Include.ALWAYS)
    @Schema(
            name = "QuizHistoryQuestion",
            description = "Immutable question, persisted shuffled options and submitted answer")
    public record Question(
            Long sessionQuestionId,
            int questionNumber,
            String sentenceReading,
            int targetStart,
            int targetLength,
            List<Option> options,
            Long selectedOptionId,
            Long correctOptionId,
            boolean correct,
            String explanationVi,
            String explanationEn,
            LocalDateTime answeredAt
    ) {
    }
}
