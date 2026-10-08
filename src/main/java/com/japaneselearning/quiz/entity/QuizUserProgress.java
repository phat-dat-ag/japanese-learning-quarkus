package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "quiz_user_progress")
public class QuizUserProgress extends QuizMutableEntity {
    @Column(name = "user_subject", nullable = false, length = 255)
    public String userSubject;

    @Column(name = "question_id", nullable = false)
    public Long questionId;

    @Column(name = "attempts", nullable = false)
    public long attempts;

    @Column(name = "correct_answers", nullable = false)
    public long correctAnswers;

    @Column(name = "last_answered_at")
    public LocalDateTime lastAnsweredAt;
}
