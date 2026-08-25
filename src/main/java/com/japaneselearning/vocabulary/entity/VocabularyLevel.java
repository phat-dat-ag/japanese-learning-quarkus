package com.japaneselearning.vocabulary.entity;

import com.japaneselearning.vocabulary.entity.id.VocabularyLevelId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "vocabulary_levels")
@IdClass(VocabularyLevelId.class)
public class VocabularyLevel {

    @Id
    @Column(name = "vocabulary_id")
    public Long vocabularyId;

    @Id
    @Column(name = "level_id")
    public Long levelId;

    @Column(name = "display_order")
    public Integer displayOrder;
}