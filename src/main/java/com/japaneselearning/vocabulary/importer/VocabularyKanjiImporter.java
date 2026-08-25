package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.KanjiReadingImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyKanjiImportItem;
import com.japaneselearning.vocabulary.repository.KanjiReadingRepository;
import com.japaneselearning.vocabulary.repository.KanjiRepository;
import com.japaneselearning.vocabulary.repository.VocabularyKanjiRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyKanjiImporter {

    private final KanjiRepository kanjiRepository;
    private final KanjiReadingRepository kanjiReadingRepository;
    private final VocabularyKanjiRepository vocabularyKanjiRepository;

    public VocabularyKanjiImporter(
            KanjiRepository kanjiRepository,
            KanjiReadingRepository kanjiReadingRepository,
            VocabularyKanjiRepository vocabularyKanjiRepository) {

        this.kanjiRepository = kanjiRepository;
        this.kanjiReadingRepository = kanjiReadingRepository;
        this.vocabularyKanjiRepository = vocabularyKanjiRepository;
    }

    public Uni<Void> importKanji(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.kanji == null || item.kanji.isEmpty()) {
            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.kanji)
                .onItem()
                .transformToUniAndConcatenate(kanjiItem ->
                        getOrCreateKanji(kanjiItem)
                                .flatMap(kanji ->
                                        vocabularyKanjiRepository
                                                .insert(
                                                        vocabulary.id,
                                                        kanji.id,
                                                        item.kanji.indexOf(kanjiItem)
                                                )
                                                .chain(() ->
                                                        importKanjiReadings(
                                                                kanji,
                                                                kanjiItem
                                                        )
                                                )
                                )
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private Uni<Kanji> getOrCreateKanji(
            VocabularyKanjiImportItem item) {

        return kanjiRepository
                .findByCharacter(item.character)
                .flatMap(existing -> {

                    if (existing != null) {

                        existing.strokeCount =
                                item.strokeCount;

                        existing.meaningVi =
                                item.meaningVi;

                        existing.meaningEn =
                                item.meaningEn;

                        return Uni.createFrom()
                                .item(existing);
                    }

                    Kanji kanji = new Kanji();

                    kanji.character =
                            item.character;

                    kanji.strokeCount =
                            item.strokeCount;

                    kanji.meaningVi =
                            item.meaningVi;

                    kanji.meaningEn =
                            item.meaningEn;

                    return kanjiRepository
                            .persist(kanji)
                            .replaceWith(kanji);
                });
    }

    private Uni<Void> importKanjiReadings(
            Kanji kanji,
            VocabularyKanjiImportItem item) {

        if (item.readings == null ||
                item.readings.isEmpty()) {

            return Uni.createFrom().voidItem();
        }

        return Multi.createFrom()
                .iterable(item.readings)
                .onItem()
                .transformToUniAndConcatenate(
                        readingItem ->
                                getOrCreateKanjiReading(
                                        kanji,
                                        readingItem
                                )
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private Uni<KanjiReading> getOrCreateKanjiReading(
            Kanji kanji,
            KanjiReadingImportItem readingItem) {

        return kanjiReadingRepository
                .findByKanjiIdAndReadingAndType(
                        kanji.id,
                        readingItem.reading,
                        readingItem.readingType
                )
                .flatMap(existing -> {

                    if (existing != null) {

                        existing.displayOrder =
                                readingItem.displayOrder;

                        return Uni.createFrom()
                                .item(existing);
                    }

                    KanjiReading reading =
                            new KanjiReading();

                    reading.kanjiId =
                            kanji.id;

                    reading.reading =
                            readingItem.reading;

                    reading.readingType =
                            readingItem.readingType;

                    reading.displayOrder =
                            readingItem.displayOrder;

                    return kanjiReadingRepository
                            .persist(reading)
                            .replaceWith(reading);
                });
    }
}