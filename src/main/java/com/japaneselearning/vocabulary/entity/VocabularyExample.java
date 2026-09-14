package com.japaneselearning.vocabulary.entity;

import com.japaneselearning.vocabulary.entity.id.VocabularyExampleId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;

@Entity
@Table(name = "vocabulary_examples")
@IdClass(VocabularyExampleId.class)
public class VocabularyExample {

    @Id
    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Id
    @Column(name = "example_sentence_id", nullable = false)
    public Long exampleSentenceId;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;

    @Column(name = "target_text", nullable = false, length = 200)
    public String targetText;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "example_sentence_id", insertable = false, updatable = false)
    public ExampleSentence exampleSentence;
}
