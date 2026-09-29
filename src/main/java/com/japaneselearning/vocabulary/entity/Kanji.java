package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "kanji")
public class Kanji {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "kanji_character", nullable = false, length = 10)
    public String character;

    // Reactive MySQL metadata exposes the base type; Flyway owns UNSIGNED. Integer preserves
    // 0..65535.
    @Column(name = "stroke_count", columnDefinition = "smallint")
    public Integer strokeCount;

    @Column(name = "meaning_vi", length = 500)
    public String meaningVi;

    @Column(name = "meaning_en", length = 500)
    public String meaningEn;
}
