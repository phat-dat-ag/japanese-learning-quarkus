package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

@Entity
@Table(name = "kanji")
public class Kanji {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "kanji_character", nullable = false, length = 10)
    public String character;

    @Column(name = "stroke_count")
    public Integer strokeCount;

    @Column(name = "meaning_vi", length = 500)
    public String meaningVi;

    @Column(name = "meaning_en", length = 500)
    public String meaningEn;
}
