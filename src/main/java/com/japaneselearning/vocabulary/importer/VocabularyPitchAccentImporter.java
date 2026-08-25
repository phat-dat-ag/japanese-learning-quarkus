package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.VocabularyPitchAccentRepository;
import com.japaneselearning.vocabulary.repository.VocabularyReadingRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyPitchAccentImporter {

    private final VocabularyReadingRepository readingRepository;
    private final VocabularyPitchAccentRepository pitchAccentRepository;

    public VocabularyPitchAccentImporter(
            VocabularyReadingRepository readingRepository,
            VocabularyPitchAccentRepository pitchAccentRepository) {

        this.readingRepository = readingRepository;
        this.pitchAccentRepository =
                pitchAccentRepository;
    }

    public Uni<Void> importPitchAccents(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.pitchAccents == null ||
                item.pitchAccents.isEmpty()) {

            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.pitchAccents)
                .onItem()
                .transformToUniAndConcatenate(accent ->
                        readingRepository
                                .findByVocabularyIdAndReading(
                                        vocabulary.id,
                                        accent.reading
                                )
                                .flatMap(reading -> {

                                    if (reading == null) {
                                        return Uni.createFrom()
                                                .failure(
                                                        new IllegalArgumentException(
                                                                "Pitch accent references unknown reading: "
                                                                        + accent.reading
                                                        )
                                                );
                                    }

                                    VocabularyPitchAccent entity =
                                            new VocabularyPitchAccent();

                                    entity.vocabularyReadingId =
                                            reading.id;

                                    entity.accentPattern =
                                            accent.accentPattern;

                                    return pitchAccentRepository
                                            .persist(entity);
                                })
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }
}