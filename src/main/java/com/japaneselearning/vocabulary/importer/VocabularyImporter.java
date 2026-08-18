package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.importer.repository.VocabularyRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class VocabularyImporter {

    private final VocabularyRepository vocabularyRepository;

    @Inject
    public VocabularyImporter(
            VocabularyRepository vocabularyRepository) {

        this.vocabularyRepository = vocabularyRepository;
    }

    public Uni<Vocabulary> importVocabulary(
            VocabularyImportItem item) {

        return vocabularyRepository
                .findByWord(item.word)
                .onItem()
                .ifNotNull()
                .transform(existing -> existing)
                .onItem()
                .ifNull()
                .switchTo(() -> createVocabulary(item));
    }

    private Uni<Vocabulary> createVocabulary(
            VocabularyImportItem item) {

        Vocabulary vocabulary = new Vocabulary();

        vocabulary.word = item.word;
        vocabulary.normalizedWord = item.normalizedWord;

        return vocabularyRepository
                .persist(vocabulary)
                .replaceWith(vocabulary);
    }
}