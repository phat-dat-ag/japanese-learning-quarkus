package com.japaneselearning.flashcard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "flashcard_session_items")
public class FlashcardSessionItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "session_id", nullable = false)
    public Long sessionId;

    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Column(nullable = false)
    public int position;

    @Column(name = "completed_at")
    public LocalDateTime completedAt;
}
