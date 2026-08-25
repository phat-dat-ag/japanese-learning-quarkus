package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

@Entity
@Table(name = "vocabulary_pitch_accents")
public class VocabularyPitchAccent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "vocabulary_reading_id", nullable = false)
    public Long vocabularyReadingId;

    @Column(name = "accent_pattern", nullable = false)
    public Integer accentPattern;
}
