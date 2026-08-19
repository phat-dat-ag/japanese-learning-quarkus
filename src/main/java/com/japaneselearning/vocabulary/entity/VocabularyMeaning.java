package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "vocabulary_meanings")
public class VocabularyMeaning {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Column(name = "language_code", nullable = false, length = 5)
    public String languageCode;

    @Column(nullable = false, length = 500)
    public String meaning;

    @Column(name = "is_primary", nullable = false)
    public Boolean isPrimary;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;
}