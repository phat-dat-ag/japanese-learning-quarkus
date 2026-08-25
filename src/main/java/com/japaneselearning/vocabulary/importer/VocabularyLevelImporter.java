package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.VocabularyLevelRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyLevelImporter {

    private final JlptLevelRepository jlptLevelRepository;
    private final VocabularyLevelRepository vocabularyLevelRepository;

    public VocabularyLevelImporter(
            JlptLevelRepository jlptLevelRepository,
            VocabularyLevelRepository vocabularyLevelRepository) {

        this.jlptLevelRepository = jlptLevelRepository;
        this.vocabularyLevelRepository = vocabularyLevelRepository;
    }

    public Uni<Void> importLevels(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.levels == null || item.levels.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.levels)
                .onItem()
                .transformToUniAndConcatenate(levelCode -> {

                    int displayOrder =
                            item.levels.indexOf(levelCode) + 1;

                    return jlptLevelRepository
                            .findByCode(levelCode)
                            .flatMap(level -> {

                                if (level == null) {
                                    return Uni.createFrom()
                                            .failure(
                                                    new IllegalArgumentException(
                                                            "Unknown JLPT level: "
                                                                    + levelCode
                                                    )
                                            );
                                }

                                return vocabularyLevelRepository
                                        .insert(
                                                vocabulary.id,
                                                level.id,
                                                displayOrder
                                        );
                            });
                })
                .collect()
                .asList()
                .replaceWithVoid();
    }
}