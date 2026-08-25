package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.VocabularyReadingRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyReadingImporter {

    private final VocabularyReadingRepository repository;

    public VocabularyReadingImporter(
            VocabularyReadingRepository repository) {

        this.repository = repository;
    }

    public Uni<Void> importReadings(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.readings == null ||
                item.readings.isEmpty()) {

            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.readings)
                .onItem()
                .transformToUniAndConcatenate(readingItem -> {

                    VocabularyReading reading =
                            new VocabularyReading();

                    reading.vocabularyId =
                            vocabulary.id;

                    reading.reading =
                            readingItem.reading;

                    reading.isPrimary =
                            readingItem.isPrimary;

                    reading.displayOrder =
                            readingItem.displayOrder;

                    return repository
                            .persist(reading)
                            .replaceWith(reading);
                })
                .collect()
                .asList()
                .replaceWithVoid();
    }
}