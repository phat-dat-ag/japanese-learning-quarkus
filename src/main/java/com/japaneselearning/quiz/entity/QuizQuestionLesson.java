package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import com.japaneselearning.quiz.entity.id.QuizQuestionLessonId;

@Entity
@Table(name = "quiz_question_lessons")
@IdClass(QuizQuestionLessonId.class)
public class QuizQuestionLesson {
    @Id
    @Column(name = "question_id", nullable = false)
    public Long questionId;

    @Id
    @Column(name = "lesson_id", nullable = false)
    public Long lessonId;
}
