package com.japaneselearning.flashcard.domain;

public enum FlashcardSessionMode {
    NEW_AND_DUE, DUE, ALL;

    public boolean scheduling() {
        return this != ALL;
    }
}
