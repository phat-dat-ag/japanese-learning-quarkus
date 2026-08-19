package com.japaneselearning.vocabulary.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.vocabulary.entity.ExampleSentence;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import com.japaneselearning.vocabulary.entity.VocabularyReading;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyKanjiImportItem;
import com.japaneselearning.vocabulary.repository.ExampleSentenceRepository;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.KanjiReadingRepository;
import com.japaneselearning.vocabulary.repository.KanjiRepository;
import com.japaneselearning.vocabulary.repository.PartOfSpeechRepository;
import com.japaneselearning.vocabulary.repository.VocabularyExampleRepository;
import com.japaneselearning.vocabulary.repository.VocabularyKanjiRepository;
import com.japaneselearning.vocabulary.repository.VocabularyLevelRepository;
import com.japaneselearning.vocabulary.repository.VocabularyMeaningRepository;
import com.japaneselearning.vocabulary.repository.VocabularyPartOfSpeechRepository;
import com.japaneselearning.vocabulary.repository.VocabularyPitchAccentRepository;
import com.japaneselearning.vocabulary.repository.VocabularyReadingRepository;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class VocabularyImporter {

    private final ObjectMapper objectMapper;

    private final VocabularyRepository vocabularyRepository;
    private final JlptLevelRepository jlptLevelRepository;

    private final VocabularyLevelRepository vocabularyLevelRepository;
    private final VocabularyReadingRepository vocabularyReadingRepository;
    private final VocabularyMeaningRepository vocabularyMeaningRepository;
    private final VocabularyPartOfSpeechRepository vocabularyPartOfSpeechRepository;
    private final VocabularyKanjiRepository vocabularyKanjiRepository;
    private final VocabularyExampleRepository vocabularyExampleRepository;

    private final PartOfSpeechRepository partOfSpeechRepository;

    private final KanjiRepository kanjiRepository;
    private final KanjiReadingRepository kanjiReadingRepository;

    private final VocabularyPitchAccentRepository pitchAccentRepository;

    private final ExampleSentenceRepository exampleSentenceRepository;

    public VocabularyImporter(
            ObjectMapper objectMapper,
            VocabularyRepository vocabularyRepository,
            JlptLevelRepository jlptLevelRepository,
            VocabularyLevelRepository vocabularyLevelRepository,
            VocabularyReadingRepository vocabularyReadingRepository,
            VocabularyMeaningRepository vocabularyMeaningRepository,
            VocabularyPartOfSpeechRepository vocabularyPartOfSpeechRepository,
            VocabularyKanjiRepository vocabularyKanjiRepository,
            VocabularyExampleRepository vocabularyExampleRepository,
            PartOfSpeechRepository partOfSpeechRepository,
            KanjiRepository kanjiRepository,
            KanjiReadingRepository kanjiReadingRepository,
            VocabularyPitchAccentRepository pitchAccentRepository,
            ExampleSentenceRepository exampleSentenceRepository) {

        this.objectMapper = objectMapper;
        this.vocabularyRepository = vocabularyRepository;
        this.jlptLevelRepository = jlptLevelRepository;
        this.vocabularyLevelRepository = vocabularyLevelRepository;
        this.vocabularyReadingRepository = vocabularyReadingRepository;
        this.vocabularyMeaningRepository = vocabularyMeaningRepository;
        this.vocabularyPartOfSpeechRepository =
                vocabularyPartOfSpeechRepository;
        this.vocabularyKanjiRepository =
                vocabularyKanjiRepository;
        this.vocabularyExampleRepository =
                vocabularyExampleRepository;
        this.partOfSpeechRepository = partOfSpeechRepository;
        this.kanjiRepository = kanjiRepository;
        this.kanjiReadingRepository = kanjiReadingRepository;
        this.pitchAccentRepository = pitchAccentRepository;
        this.exampleSentenceRepository = exampleSentenceRepository;
    }

    public Uni<ImportResult> importVocabulary(Path file) {

        final List<VocabularyImportItem> items;

        try {
            items = objectMapper.readValue(
                    file.toFile(),
                    new TypeReference<List<VocabularyImportItem>>() {
                    }
            );
        } catch (IOException e) {
            return Uni.createFrom()
                    .failure(
                            new IllegalArgumentException(
                                    "Invalid vocabulary JSON file",
                                    e
                            )
                    );
        }

        return Multi.createFrom()
                .iterable(items)
                .onItem()
                .transformToUniAndConcatenate(this::importItem)
                .collect()
                .asList()
                .map(results -> {

                    int created = (int) results.stream()
                            .filter(
                                    result ->
                                            result == ImportStatus.CREATED
                            )
                            .count();

                    int updated = (int) results.stream()
                            .filter(
                                    result ->
                                            result == ImportStatus.UPDATED
                            )
                            .count();

                    return new ImportResult(
                            items.size(),
                            created,
                            updated
                    );
                });
    }

    private Uni<ImportStatus> importItem(
            VocabularyImportItem item) {

        return getOrCreateVocabulary(item)
                .flatMap(vocabulary ->
                        clearVocabularyChildren(vocabulary.id)
                                .chain(() ->
                                        importLevels(
                                                vocabulary,
                                                item
                                        )
                                )
                                .chain(() ->
                                        importReadings(
                                                vocabulary,
                                                item
                                        )
                                )
                                .chain(() ->
                                        importMeanings(
                                                vocabulary,
                                                item
                                        )
                                )
                                .chain(() ->
                                        importPartsOfSpeech(
                                                vocabulary,
                                                item
                                        )
                                )
                                .chain(() ->
                                        importKanji(
                                                vocabulary,
                                                item
                                        )
                                )
                                .chain(() ->
                                        importPitchAccents(
                                                vocabulary,
                                                item
                                        )
                                )
                                .chain(() ->
                                        importExamples(
                                                vocabulary,
                                                item
                                        )
                                )
                                .replaceWith(
                                        ImportStatus.UPDATED
                                )
                );
    }

    private Uni<Vocabulary> getOrCreateVocabulary(
            VocabularyImportItem item) {

        return vocabularyRepository
                .findByNormalizedWord(item.normalizedWord)
                .flatMap(existing -> {

                    if (existing != null) {

                        existing.word = item.word;
                        existing.normalizedWord =
                                item.normalizedWord;

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

    private Uni<Void> clearVocabularyChildren(
            Long vocabularyId) {

        return vocabularyExampleRepository
                .deleteByVocabularyId(vocabularyId)
                .chain(() ->
                        vocabularyLevelRepository
                                .deleteByVocabularyId(
                                        vocabularyId
                                )
                )
                .chain(() ->
                        vocabularyPartOfSpeechRepository
                                .deleteByVocabularyId(
                                        vocabularyId
                                )
                )
                .chain(() ->
                        vocabularyKanjiRepository
                                .deleteByVocabularyId(
                                        vocabularyId
                                )
                )
                .chain(() ->
                        deletePitchAccentsAndReadings(
                                vocabularyId
                        )
                )
                .chain(() ->
                        vocabularyMeaningRepository
                                .deleteByVocabularyId(
                                        vocabularyId
                                )
                );
    }

    private Uni<Void> deletePitchAccentsAndReadings(
            Long vocabularyId) {

        return vocabularyReadingRepository
                .find(
                        "vocabularyId",
                        vocabularyId
                )
                .list()
                .flatMap(readings ->
                        Multi.createFrom()
                                .iterable(readings)
                                .onItem()
                                .transformToUniAndConcatenate(
                                        reading ->
                                                pitchAccentRepository
                                                        .deleteByVocabularyReadingId(
                                                                reading.id
                                                        )
                                )
                                .collect()
                                .asList()
                                .replaceWithVoid()
                )
                .chain(() ->
                        vocabularyReadingRepository
                                .deleteByVocabularyId(
                                        vocabularyId
                                )
                );
    }

    private Uni<Void> importLevels(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.levels == null ||
                item.levels.isEmpty()) {

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.levels)
                .onItem()
                .transformToUniAndConcatenate(
                        levelCode ->
                                jlptLevelRepository
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
                                                            level.id
                                                    );
                                        })
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private Uni<Void> importReadings(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.readings == null ||
                item.readings.isEmpty()) {

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.readings)
                .onItem()
                .transformToUniAndConcatenate(
                        readingItem -> {

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

                            return vocabularyReadingRepository
                                    .persist(reading);
                        }
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private Uni<Void> importMeanings(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.meanings == null ||
                item.meanings.isEmpty()) {

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.meanings)
                .onItem()
                .transformToUniAndConcatenate(
                        meaningItem -> {

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

                            return vocabularyMeaningRepository
                                    .persist(meaning);
                        }
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private Uni<Void> importPartsOfSpeech(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.partsOfSpeech == null ||
                item.partsOfSpeech.isEmpty()) {

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.partsOfSpeech)
                .onItem()
                .transformToUniAndConcatenate(
                        code ->
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

    private Uni<Void> importKanji(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.kanji == null ||
                item.kanji.isEmpty()) {

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.kanji)
                .onItem()
                .transformToUniAndConcatenate(
                        kanjiItem ->
                                getOrCreateKanji(
                                        kanjiItem
                                )
                                        .flatMap(kanji ->
                                                vocabularyKanjiRepository
                                                        .insert(
                                                                vocabulary.id,
                                                                kanji.id,
                                                                item.kanji
                                                                        .indexOf(
                                                                                kanjiItem
                                                                        )
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

                    Kanji kanji =
                            new Kanji();

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

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.readings)
                .onItem()
                .transformToUniAndConcatenate(
                        readingItem -> {

                            return kanjiReadingRepository
                                    .find(
                                            "kanjiId = ?1 and reading = ?2 and readingType = ?3",
                                            kanji.id,
                                            readingItem.reading,
                                            readingItem.readingType
                                    )
                                    .firstResult()
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
                                                .persist(reading);
                                    });
                        }
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private Uni<Void> importPitchAccents(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.pitchAccents == null ||
                item.pitchAccents.isEmpty()) {

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.pitchAccents)
                .onItem()
                .transformToUniAndConcatenate(
                        accentItem ->
                                vocabularyReadingRepository
                                        .findByVocabularyIdAndReading(
                                                vocabulary.id,
                                                accentItem.reading
                                        )
                                        .flatMap(reading -> {

                                            if (reading == null) {
                                                return Uni.createFrom()
                                                        .failure(
                                                                new IllegalArgumentException(
                                                                        "Reading not found for pitch accent: "
                                                                                + accentItem.reading
                                                                )
                                                        );
                                            }

                                            VocabularyPitchAccent accent =
                                                    new VocabularyPitchAccent();

                                            accent.vocabularyReadingId =
                                                    reading.id;

                                            accent.accentPattern =
                                                    accentItem.accentPattern;

                                            return pitchAccentRepository
                                                    .persist(accent);
                                        })
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private Uni<Void> importExamples(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        if (item.examples == null ||
                item.examples.isEmpty()) {

            return Uni.createFrom()
                    .voidItem();
        }

        return Multi.createFrom()
                .iterable(item.examples)
                .onItem()
                .transformToUniAndConcatenate(
                        exampleItem -> {

                            ExampleSentence sentence =
                                    new ExampleSentence();

                            sentence.japaneseText =
                                    exampleItem.japaneseText;

                            sentence.japaneseReading =
                                    exampleItem.japaneseReading;

                            sentence.meaningVi =
                                    exampleItem.meaningVi;

                            sentence.meaningEn =
                                    exampleItem.meaningEn;

                            return exampleSentenceRepository
                                    .persist(sentence)
                                    .flatMap(savedSentence ->
                                            vocabularyExampleRepository
                                                    .insert(
                                                            vocabulary.id,
                                                            savedSentence.id,
                                                            exampleItem.targetText,
                                                            exampleItem.displayOrder
                                                    )
                                    );
                        }
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    private enum ImportStatus {
        CREATED,
        UPDATED
    }
}