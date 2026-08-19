package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

@Entity
@Table(name = "example_sentences")
public class ExampleSentence {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "japanese_text", nullable = false, length = 1000)
    public String japaneseText;

    @Column(name = "japanese_reading", nullable = false, length = 1000)
    public String japaneseReading;

    @Column(name = "meaning_vi", nullable = false, length = 1000)
    public String meaningVi;

    @Column(name = "meaning_en", nullable = false, length = 1000)
    public String meaningEn;
}