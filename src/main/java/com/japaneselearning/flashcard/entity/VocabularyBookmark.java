package com.japaneselearning.flashcard.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "user_vocabulary_bookmarks")
public class VocabularyBookmark {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "user_subject", nullable = false, length = 255)
    public String userSubject;

    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Column(name = "created_at", nullable = false, updatable = false)
    public LocalDateTime createdAt;
}
