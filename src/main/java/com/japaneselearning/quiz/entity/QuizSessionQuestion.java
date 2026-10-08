package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;

import java.time.LocalDateTime;

import com.japaneselearning.quiz.domain.QuestionSource;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "quiz_session_questions")
@Immutable
public class QuizSessionQuestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "session_id", nullable = false)
    public Long sessionId;

    @Column(name = "question_id")
    public Long questionId;

    @Column(name = "question_version", nullable = false)
    public long questionVersion;

    @Column(name = "question_number", nullable = false)
    public int questionNumber;

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "source_type", nullable = false, length = 16)
    public QuestionSource sourceType;

    @Column(name = "source_example_id")
    public Long sourceExampleId;

    @Column(name = "sentence_reading", nullable = false, length = 1000)
    public String sentenceReading;

    @Column(name = "target_start", nullable = false)
    public int targetStart;

    @Column(name = "target_length", nullable = false)
    public int targetLength;

    @Column(name = "target_reading", nullable = false, length = 200)
    public String targetReading;

    @Column(name = "explanation_vi", length = 2000)
    public String explanationVi;

    @Column(name = "explanation_en", length = 2000)
    public String explanationEn;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;
}
