package com.japaneselearning.quiz.player.dto;

import org.eclipse.microprofile.openapi.annotations.media.Schema;

import java.util.List;

@Schema(description = "Playable bank counts; only levels and lessons with a positive count are listed")
public record QuizGameConfigResponse(
        long totalQuestions,
        int maxQuestionCount,
        List<Level> levels,
        List<Lesson> lessons
) {
    @Schema(name = "QuizGameLevel")
    public record Level(
            Long id,
            String code,
            String name,
            long questionCount
    ) {
    }

    @Schema(name = "QuizGameLesson")
    public record Lesson(
            Long id,
            Long levelId,
            int lessonNumber,
            String title,
            long questionCount
    ) {
    }
}
