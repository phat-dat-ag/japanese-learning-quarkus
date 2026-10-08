package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;

@Entity
@Table(name = "quiz_questions")
public class QuizQuestion extends QuizMutableEntity {
    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "source_type", nullable = false, length = 16)
    public QuestionSource sourceType;

    @Column(name = "example_sentence_id")
    public Long exampleSentenceId;

    @Column(name = "vocabulary_id")
    public Long vocabularyId;

    @Column(name = "sentence_reading", length = 1000)
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

    @Enumerated(EnumType.STRING)
    @org.hibernate.annotations.JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(name = "status", nullable = false, length = 16)
    public QuestionStatus status;

    @Column(name = "validated_example_fingerprint", length = 64)
    public String validatedExampleFingerprint;
}
