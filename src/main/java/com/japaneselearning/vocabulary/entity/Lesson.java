package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "level_id", nullable = false)
    public Long levelId;

    @Column(name = "lesson_number", nullable = false)
    public Integer lessonNumber;

    @Column(nullable = false, length = 200)
    public String title;

    @Column(length = 1000)
    public String description;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;

    @Column(name = "created_at", nullable = false)
    public LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    public LocalDateTime updatedAt;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "level_id", insertable = false, updatable = false)
    public JlptLevel level;
}