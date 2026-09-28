package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.exception.ConstraintViolationException;

@ApplicationScoped
public class VocabularyCoreImporter {

    private final VocabularyRepository vocabularyRepository;

    public VocabularyCoreImporter(
            VocabularyRepository vocabularyRepository) {

        this.vocabularyRepository = vocabularyRepository;
    }

    public Uni<Vocabulary> findExisting(String normalizedWord) {
        return vocabularyRepository.findByNormalizedWord(normalizedWord);
    }

    public Uni<Vocabulary> create(VocabularyImportItem item) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.word = item.word;
        vocabulary.normalizedWord = item.normalizedWord;

        return vocabularyRepository.persistAndFlush(vocabulary)
                .onFailure(failure -> failure instanceof ConstraintViolationException constraint
                        && "uk_vocabulary_normalized_word".equals(constraint.getConstraintName()))
                .transform(failure -> new ConflictException("VOCABULARY_ALREADY_EXISTS",
                        "Vocabulary already exists. Retry the batch to validate its assignments."));
    }
}
