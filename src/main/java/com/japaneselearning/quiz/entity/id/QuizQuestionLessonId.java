package com.japaneselearning.quiz.entity.id;

import java.io.Serializable;
import java.util.Objects;

public class QuizQuestionLessonId implements Serializable {
    public Long questionId;
    public Long lessonId;

    public QuizQuestionLessonId() {
    }

    public QuizQuestionLessonId(Long questionId, Long lessonId) {
        this.questionId = questionId;
        this.lessonId = lessonId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof QuizQuestionLessonId that
                && Objects.equals(questionId, that.questionId) && Objects.equals(lessonId, that.lessonId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(questionId, lessonId);
    }
}
