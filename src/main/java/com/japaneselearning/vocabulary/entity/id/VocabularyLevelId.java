package com.japaneselearning.vocabulary.entity.id;

import java.io.Serializable;
import java.util.Objects;

public class VocabularyLevelId implements Serializable {

    public Long vocabularyId;
    public Long levelId;

    public VocabularyLevelId(Long vocabularyId, Long levelId) {
        this.vocabularyId = vocabularyId;
        this.levelId = levelId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }

        if (!(o instanceof VocabularyLevelId that)) {
            return false;
        }

        return Objects.equals(vocabularyId, that.vocabularyId)
                && Objects.equals(levelId, that.levelId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vocabularyId, levelId);
    }
}