package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Column;

@Entity
@Table(name = "kanji_readings")
public class KanjiReading {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    @Column(name = "kanji_id", nullable = false)
    public Long kanjiId;

    @Column(nullable = false, length = 100)
    public String reading;

    @Column(name = "reading_type", nullable = false, length = 20)
    public String readingType;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;
}
