package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.PartOfSpeechRepository;
import com.japaneselearning.vocabulary.repository.VocabularyPartOfSpeechRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyPartOfSpeechImporter {

    private final PartOfSpeechRepository partOfSpeechRepository;
    private final VocabularyPartOfSpeechRepository vocabularyPartOfSpeechRepository;

    public VocabularyPartOfSpeechImporter(
            PartOfSpeechRepository partOfSpeechRepository,
            VocabularyPartOfSpeechRepository vocabularyPartOfSpeechRepository) {

        this.partOfSpeechRepository =
                partOfSpeechRepository;

        this.vocabularyPartOfSpeechRepository =
                vocabularyPartOfSpeechRepository;
    }

    public Uni<Void> importPartsOfSpeech(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.partsOfSpeech == null ||
                item.partsOfSpeech.isEmpty()) {

            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.partsOfSpeech)
                .onItem()
                .transformToUniAndConcatenate(code ->
                        partOfSpeechRepository
                                .findByCode(code)
                                .flatMap(pos -> {

                                    if (pos == null) {
                                        return Uni.createFrom()
                                                .failure(
                                                        new IllegalArgumentException(
                                                                "Unknown part of speech: "
                                                                        + code
                                                        )
                                                );
                                    }

                                    return vocabularyPartOfSpeechRepository
                                            .insert(
                                                    vocabulary.id,
                                                    pos.id
                                            );
                                })
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }
}