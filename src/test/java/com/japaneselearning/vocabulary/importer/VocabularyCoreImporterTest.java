package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import io.smallrye.mutiny.Uni;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VocabularyCoreImporterTest {
    @Test
    void mapsOnlyTheCanonicalIdentityConstraintToConflict() {
        var duplicate = new ConstraintViolationException("internal details", new SQLException(),
                "uk_vocabulary_normalized_word");
        var importer = importerFailingWith(duplicate);
        ConflictException error = assertThrows(ConflictException.class,
                () -> importer.create(item()).await().atMost(Duration.ofSeconds(1)));
        assertEquals("VOCABULARY_ALREADY_EXISTS", error.getCode());
        assertEquals("Vocabulary already exists. Retry the batch to validate its assignments.", error.getMessage());
    }

    @Test
    void neverSwallowsOtherConstraintFailures() {
        var failure = new ConstraintViolationException("internal details", new SQLException(), "another_constraint");
        assertSame(failure, assertThrows(ConstraintViolationException.class,
                () -> importerFailingWith(failure).create(item()).await().atMost(Duration.ofSeconds(1))));
    }

    private VocabularyCoreImporter importerFailingWith(RuntimeException failure) {
        return new VocabularyCoreImporter(new VocabularyRepository() {
            @Override
            public Uni<Vocabulary> persistAndFlush(Vocabulary vocabulary) {
                return Uni.createFrom().failure(failure);
            }
        });
    }

    private VocabularyImportItem item() {
        VocabularyImportItem item = new VocabularyImportItem();
        item.word = "word";
        item.normalizedWord = "word";
        return item;
    }
}
