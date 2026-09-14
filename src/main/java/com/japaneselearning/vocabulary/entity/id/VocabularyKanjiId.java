package com.japaneselearning.vocabulary.entity.id;

import java.io.Serializable;
import java.util.Objects;

public class VocabularyKanjiId implements Serializable {

    public Long vocabularyId;
    public Long kanjiId;

    public VocabularyKanjiId() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof VocabularyKanjiId that)) {
            return false;
        }
        return Objects.equals(vocabularyId, that.vocabularyId)
                && Objects.equals(kanjiId, that.kanjiId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(vocabularyId, kanjiId);
    }
}
