package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;

import java.time.LocalDateTime;

import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "quiz_answers")
@Immutable
public class QuizAnswer {
    @Id
    @Column(name = "session_question_id")
    public Long sessionQuestionId;

    @Column(name = "selected_option_id", nullable = false)
    public Long selectedOptionId;

    @Column(name = "answered_at", nullable = false)
    public LocalDateTime answeredAt;
}
