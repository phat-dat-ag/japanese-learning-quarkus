package com.japaneselearning.vocabulary.entity;

import com.japaneselearning.vocabulary.entity.id.VocabularyPartOfSpeechId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

@Entity
@Table(name = "vocabulary_parts_of_speech")
@IdClass(VocabularyPartOfSpeechId.class)
public class VocabularyPartOfSpeech {

    @Id
    @Column(name = "vocabulary_id")
    public Long vocabularyId;

    @Id
    @Column(name = "part_of_speech_id")
    public Long partOfSpeechId;
}