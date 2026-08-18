ALTER TABLE vocabulary
    ADD CONSTRAINT uk_vocabulary_normalized_word
    UNIQUE (normalized_word);