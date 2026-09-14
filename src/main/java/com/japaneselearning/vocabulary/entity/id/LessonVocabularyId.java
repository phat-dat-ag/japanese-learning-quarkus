package com.japaneselearning.vocabulary.entity.id;

import java.io.Serializable;
import java.util.Objects;

public class LessonVocabularyId implements Serializable {

    public Long lessonId;
    public Long vocabularyId;

    public LessonVocabularyId() {
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof LessonVocabularyId that)) {
            return false;
        }
        return Objects.equals(lessonId, that.lessonId)
                && Objects.equals(vocabularyId, that.vocabularyId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(lessonId, vocabularyId);
    }
}
