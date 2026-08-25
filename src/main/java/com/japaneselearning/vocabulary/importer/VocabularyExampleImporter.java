package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.ExampleSentence;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.ExampleSentenceRepository;
import com.japaneselearning.vocabulary.repository.VocabularyExampleRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyExampleImporter {

    private final ExampleSentenceRepository exampleSentenceRepository;
    private final VocabularyExampleRepository vocabularyExampleRepository;

    public VocabularyExampleImporter(
            ExampleSentenceRepository exampleSentenceRepository,
            VocabularyExampleRepository vocabularyExampleRepository) {

        this.exampleSentenceRepository =
                exampleSentenceRepository;

        this.vocabularyExampleRepository =
                vocabularyExampleRepository;
    }

    public Uni<Void> importExamples(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.examples == null ||
                item.examples.isEmpty()) {

            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.examples)
                .onItem()
                .transformToUniAndConcatenate(exampleItem -> {

                    ExampleSentence example =
                            new ExampleSentence();

                    example.japaneseText =
                            exampleItem.japaneseText;

                    example.japaneseReading =
                            exampleItem.japaneseReading;

                    example.meaningVi =
                            exampleItem.meaningVi;

                    example.meaningEn =
                            exampleItem.meaningEn;

                    return exampleSentenceRepository
                            .persist(example)
                            .flatMap(saved ->
                                    vocabularyExampleRepository
                                            .insert(
                                                    vocabulary.id,
                                                    saved.id,
                                                    exampleItem.targetText,
                                                    exampleItem.displayOrder
                                            )
                            );
                })
                .collect()
                .asList()
                .replaceWithVoid();
    }
}