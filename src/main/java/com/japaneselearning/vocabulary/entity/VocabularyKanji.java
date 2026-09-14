package com.japaneselearning.vocabulary.entity;

import com.japaneselearning.vocabulary.entity.id.VocabularyKanjiId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "vocabulary_kanji")
@IdClass(VocabularyKanjiId.class)
public class VocabularyKanji {

    @Id
    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Id
    @Column(name = "kanji_id", nullable = false)
    public Long kanjiId;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;
}
