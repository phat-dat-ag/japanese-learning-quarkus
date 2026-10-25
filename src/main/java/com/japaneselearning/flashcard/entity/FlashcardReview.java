package com.japaneselearning.flashcard.entity;

import com.japaneselearning.flashcard.domain.ReviewRating;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "flashcard_review_history")
public class FlashcardReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "session_item_id", nullable = false)
    public Long sessionItemId;

    @Column(name = "user_subject", nullable = false, length = 255)
    public String userSubject;

    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 8)
    public ReviewRating rating;

    @Column(nullable = false)
    @JdbcTypeCode(java.sql.Types.TINYINT)
    public boolean scheduling;

    @Column(name = "expected_progress_version", nullable = false)
    public long expectedProgressVersion;

    @Column(name = "algorithm_version", nullable = false, length = 40)
    public String algorithmVersion;

    @Column(name = "before_state", nullable = false, columnDefinition = "longtext")
    public String beforeState;

    @Column(name = "after_state", nullable = false, columnDefinition = "longtext")
    public String afterState;

    @Column(name = "reviewed_at", nullable = false)
    public LocalDateTime reviewedAt;
}
