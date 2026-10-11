package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import com.japaneselearning.quiz.entity.id.QuizQuestionLevelId;

@Entity
@Table(name = "quiz_question_levels")
@IdClass(QuizQuestionLevelId.class)
public class QuizQuestionLevel {
    @Id
    @Column(name = "question_id", nullable = false)
    public Long questionId;

    @Id
    @Column(name = "level_id", nullable = false)
    public Long levelId;
}
