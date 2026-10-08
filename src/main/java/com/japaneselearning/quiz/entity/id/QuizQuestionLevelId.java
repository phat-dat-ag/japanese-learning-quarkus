package com.japaneselearning.quiz.entity.id;

import java.io.Serializable;
import java.util.Objects;

public class QuizQuestionLevelId implements Serializable {
    public Long questionId;
    public Long levelId;

    public QuizQuestionLevelId() {
    }

    public QuizQuestionLevelId(Long questionId, Long levelId) {
        this.questionId = questionId;
        this.levelId = levelId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuizQuestionLevelId that
                && Objects.equals(questionId, that.questionId)
                && Objects.equals(levelId, that.levelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, levelId);
    }
}
