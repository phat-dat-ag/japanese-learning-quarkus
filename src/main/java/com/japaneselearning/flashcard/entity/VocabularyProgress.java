package com.japaneselearning.flashcard.entity;

import com.japaneselearning.flashcard.domain.LearningStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "user_vocabulary_progress")
public class VocabularyProgress {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_subject", nullable = false, length = 255)
    public String userSubject;

    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 16)
    public LearningStatus status;

    @Column(name = "successful_repetition_count", nullable = false)
    public long successfulRepetitionCount;

    @Column(name = "lapse_count", nullable = false)
    public long lapseCount;

    @Column(name = "interval_seconds", nullable = false)
    public long intervalSeconds;

    @Column(name = "ease_factor", nullable = false)
    public double easeFactor;

    @Column(name = "last_reviewed_at", nullable = false)
    public LocalDateTime lastReviewedAt;

    @Column(name = "next_review_at", nullable = false)
    public LocalDateTime nextReviewAt;

    @Column(name = "mastered_at")
    public LocalDateTime masteredAt;

    @Version
    public long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
