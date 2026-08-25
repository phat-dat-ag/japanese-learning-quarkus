package com.japaneselearning.vocabulary.entity.id;

import java.io.Serializable;
import java.util.Objects;

public class VocabularyPartOfSpeechId implements Serializable {

    public Long vocabularyId;
    public Long partOfSpeechId;

    public VocabularyPartOfSpeechId(Long vocabularyId, Long partOfSpeechId) {
        this.vocabularyId = vocabularyId;
        this.partOfSpeechId = partOfSpeechId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof VocabularyPartOfSpeechId that)) {
            return false;
        }

        return Objects.equals(vocabularyId, that.vocabularyId)
                && Objects.equals(partOfSpeechId, that.partOfSpeechId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vocabularyId, partOfSpeechId);
    }
}