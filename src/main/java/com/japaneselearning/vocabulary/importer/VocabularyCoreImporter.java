package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyCoreImporter {

    private final VocabularyRepository vocabularyRepository;

    public VocabularyCoreImporter(
            VocabularyRepository vocabularyRepository) {

        this.vocabularyRepository = vocabularyRepository;
    }

    public Uni<Vocabulary> getOrCreate(
            VocabularyImportItem item) {

        return vocabularyRepository
                .findByNormalizedWord(item.normalizedWord)
                .flatMap(existing -> {

                    if (existing != null) {
                        existing.word = item.word;
                        existing.normalizedWord = item.normalizedWord;

                        return Uni.createFrom()
                                .item(existing);
                    }

                    Vocabulary vocabulary =
                            new Vocabulary();

                    vocabulary.word = item.word;
                    vocabulary.normalizedWord =
                            item.normalizedWord;

                    return vocabularyRepository
                            .persist(vocabulary)
                            .replaceWith(vocabulary);
                });
    }
}