package com.japaneselearning.security;

import java.util.Set;

public class FlashcardMysqlTestProfile extends VocabularyMysqlTestProfile {
    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(CoordinatedFlashcardProgressRepository.class);
    }
}