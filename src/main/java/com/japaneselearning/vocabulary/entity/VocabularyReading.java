package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vocabulary_readings")
public class VocabularyReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Column(nullable = false, length = 100)
    public String reading;

    @Column(name = "is_primary", nullable = false)
    public Boolean isPrimary;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;
}