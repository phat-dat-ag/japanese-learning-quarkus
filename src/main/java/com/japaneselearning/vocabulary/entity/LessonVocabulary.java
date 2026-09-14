package com.japaneselearning.vocabulary.entity;

import com.japaneselearning.vocabulary.entity.id.LessonVocabularyId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "lesson_vocabulary")
@IdClass(LessonVocabularyId.class)
public class LessonVocabulary {

    @Id
    @Column(name = "lesson_id", nullable = false)
    public Long lessonId;

    @Id
    @Column(name = "vocabulary_id", nullable = false)
    public Long vocabularyId;

    @Column(name = "display_order", nullable = false)
    public Integer displayOrder;
}
