package com.japaneselearning.flashcard.entity;

import com.japaneselearning.flashcard.domain.FlashcardSessionMode;
import com.japaneselearning.flashcard.domain.FlashcardSessionStatus;
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
@Table(name = "flashcard_sessions")
public class FlashcardSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_subject", nullable = false, length = 255)
    public String userSubject;

    @Column(name = "lesson_id", nullable = false)
    public Long lessonId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 16)
    public FlashcardSessionMode mode;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(java.sql.Types.VARCHAR)
    @Column(nullable = false, length = 16)
    public FlashcardSessionStatus status;

    @Column(name = "item_count", nullable = false)
    public int itemCount;

    @Column(name = "completed_count", nullable = false)
    public int completedCount;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;

    @Column(name = "abandoned_at")
    public LocalDateTime abandonedAt;

    @Version
    public long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;
}
