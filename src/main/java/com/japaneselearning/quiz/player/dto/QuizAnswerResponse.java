package com.japaneselearning.quiz.player.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(description =
        "Feedback from immutable snapshots; " +
                "score is the number of correct submitted answers"
)
public record QuizAnswerResponse(
        Long sessionQuestionId,
        boolean correct,
        Long correctOptionId,
        String explanationVi,
        String explanationEn,
        long score,
        long answeredCount,
        long remainingCount
) {
}
