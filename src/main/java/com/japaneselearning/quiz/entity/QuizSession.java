package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import java.time.LocalDateTime;

import com.japaneselearning.quiz.domain.QuizSessionStatus;

@Entity
@Table(name = "quiz_sessions")
public class QuizSession extends QuizMutableEntity {
    @Column(name = "user_subject", nullable = false, length = 255)
    public String userSubject;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    public QuizSessionStatus status;

    @Column(name = "question_count", nullable = false)
    public int questionCount;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;
}
