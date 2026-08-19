package com.japaneselearning.vocabulary.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "parts_of_speech")
public class PartOfSpeech {

    @Id
    public Long id;

    @Column(nullable = false, unique = true, length = 50)
    public String code;

    @Column(name = "name_vi", nullable = false, length = 100)
    public String nameVi;

    @Column(name = "name_en", nullable = false, length = 100)
    public String nameEn;
}