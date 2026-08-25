package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.VocabularyMeaningRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyMeaningImporter {

    private final VocabularyMeaningRepository repository;

    public VocabularyMeaningImporter(
            VocabularyMeaningRepository repository) {

        this.repository = repository;
    }

    public Uni<Void> importMeanings(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.meanings == null ||
                item.meanings.isEmpty()) {

            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.meanings)
                .onItem()
                .transformToUniAndConcatenate(meaningItem -> {

                    VocabularyMeaning meaning =
                            new VocabularyMeaning();

                    meaning.vocabularyId =
                            vocabulary.id;

                    meaning.languageCode =
                            meaningItem.language;

                    meaning.meaning =
                            meaningItem.meaning;

                    meaning.isPrimary =
                            meaningItem.isPrimary;

                    meaning.displayOrder =
                            meaningItem.displayOrder;

                    return repository
                            .persist(meaning)
                            .replaceWith(meaning);
                })
                .collect()
                .asList()
                .replaceWithVoid();
    }
}