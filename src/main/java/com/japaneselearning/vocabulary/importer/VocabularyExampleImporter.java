package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.ExampleSentence;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyExampleImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.ExampleSentenceRepository;
import com.japaneselearning.vocabulary.repository.VocabularyExampleRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.Set;

@ApplicationScoped
public class VocabularyExampleImporter {
    private final ExampleSentenceRepository exampleSentenceRepository;
    private final VocabularyExampleRepository vocabularyExampleRepository;

    public VocabularyExampleImporter(
            ExampleSentenceRepository exampleSentenceRepository,
            VocabularyExampleRepository vocabularyExampleRepository) {

        this.exampleSentenceRepository = exampleSentenceRepository;

        this.vocabularyExampleRepository = vocabularyExampleRepository;
    }

    public Uni<Void> importExamples(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.examples == null ||
                item.examples.isEmpty()) {

            return Uni.createFrom().voidItem();
        }

        return vocabularyExampleRepository.findByVocabularyId(vocabulary.id).flatMap(existing -> {
            Set<ExampleKey> seen = new HashSet<>();
            existing.forEach(link -> seen.add(new ExampleKey(link.exampleSentence.japaneseText,
                    link.exampleSentence.japaneseReading, link.targetText)));
            return Multi.createFrom().iterable(item.examples)
                    .onItem().transformToUniAndConcatenate(example -> {
                        ExampleKey key = new ExampleKey(example.japaneseText, example.japaneseReading, example.targetText);
                        if (seen.contains(key)) {
                            return Uni.createFrom().voidItem();
                        }
                        return insert(vocabulary.id, example).invoke(() -> seen.add(key));
                    })
                    .collect().asList().replaceWithVoid();
        });
    }

    private Uni<Void> insert(Long vocabularyId, VocabularyExampleImportItem item) {
        ExampleSentence example = new ExampleSentence();
        example.japaneseText = item.japaneseText;
        example.japaneseReading = item.japaneseReading;
        example.meaningVi = item.meaningVi;
        example.meaningEn = item.meaningEn;
        return exampleSentenceRepository.persist(example)
                .flatMap(saved -> vocabularyExampleRepository.insert(
                        vocabularyId, saved.id, item.targetText, item.displayOrder));
    }

    // Per-vocabulary identity. Translations and ordering never cause an existing sentence to be rewritten.
    private record ExampleKey(String japaneseText, String japaneseReading, String targetText) {
    }
}
