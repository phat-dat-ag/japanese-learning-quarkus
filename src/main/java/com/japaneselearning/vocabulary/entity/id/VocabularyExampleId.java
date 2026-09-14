package com.japaneselearning.vocabulary.entity.id;

import java.io.Serializable;
import java.util.Objects;

public class VocabularyExampleId implements Serializable {

    public Long vocabularyId;
    public Long exampleSentenceId;

    public VocabularyExampleId() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VocabularyExampleId that)) {
            return false;
        }
        return Objects.equals(vocabularyId, that.vocabularyId)
                && Objects.equals(exampleSentenceId, that.exampleSentenceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vocabularyId, exampleSentenceId);
    }
}
