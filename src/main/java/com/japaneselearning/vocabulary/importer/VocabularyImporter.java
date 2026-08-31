package com.japaneselearning.vocabulary.importer;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

@ApplicationScoped
public class VocabularyImporter {

    private final ObjectMapper objectMapper;

    private final VocabularyImportValidator validator;

    private final VocabularyCoreImporter coreImporter;
    private final VocabularyLevelImporter levelImporter;
    private final VocabularyReadingImporter readingImporter;
    private final VocabularyMeaningImporter meaningImporter;
    private final VocabularyPartOfSpeechImporter partOfSpeechImporter;
    private final VocabularyKanjiImporter kanjiImporter;
    private final VocabularyPitchAccentImporter pitchAccentImporter;
    private final VocabularyExampleImporter exampleImporter;
    private final VocabularyLessonImporter lessonImporter;

    public VocabularyImporter(
            ObjectMapper objectMapper,
            VocabularyImportValidator validator,
            VocabularyCoreImporter coreImporter,
            VocabularyLevelImporter levelImporter,
            VocabularyReadingImporter readingImporter,
            VocabularyMeaningImporter meaningImporter,
            VocabularyPartOfSpeechImporter partOfSpeechImporter,
            VocabularyKanjiImporter kanjiImporter,
            VocabularyPitchAccentImporter pitchAccentImporter,
            VocabularyExampleImporter exampleImporter,
            VocabularyLessonImporter lessonImporter) {

        this.objectMapper = objectMapper;
        this.validator = validator;
        this.coreImporter = coreImporter;
        this.levelImporter = levelImporter;
        this.readingImporter = readingImporter;
        this.meaningImporter = meaningImporter;
        this.partOfSpeechImporter = partOfSpeechImporter;
        this.kanjiImporter = kanjiImporter;
        this.pitchAccentImporter = pitchAccentImporter;
        this.exampleImporter = exampleImporter;
        this.lessonImporter = lessonImporter;
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

        try {

            validator.validate(items);

        } catch (IllegalArgumentException e) {

            return Uni.createFrom()
                    .failure(e);
        }

        return Multi.createFrom()
                .iterable(items)
                .onItem()
                .transformToUniAndConcatenate(
                        this::importItem
                )
                .collect()
                .asList()
                .map(results -> {

                    int created =
                            (int) results.stream()
                                    .filter(
                                            ImportStatus.CREATED::equals
                                    )
                                    .count();

                    int updated =
                            (int) results.stream()
                                    .filter(
                                            ImportStatus.UPDATED::equals
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

        return coreImporter
                .getOrCreate(item)
                .flatMap(vocabulary ->
                        importRelations(
                                vocabulary,
                                item
                        )
                )
                .map(status ->
                        status
                );
    }

    private Uni<ImportStatus> importRelations(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        return levelImporter
                .importLevels(vocabulary, item)

                .chain(() ->
                        lessonImporter
                                .importLessons(
                                        vocabulary,
                                        item
                                )
                )

                .chain(() ->
                        readingImporter
                                .importReadings(
                                        vocabulary,
                                        item
                                )
                )

                .chain(() ->
                        meaningImporter
                                .importMeanings(
                                        vocabulary,
                                        item
                                )
                )

                .chain(() ->
                        partOfSpeechImporter
                                .importPartsOfSpeech(
                                        vocabulary,
                                        item
                                )
                )

                .chain(() ->
                        kanjiImporter
                                .importKanji(
                                        vocabulary,
                                        item
                                )
                )

                .chain(() ->
                        pitchAccentImporter
                                .importPitchAccents(
                                        vocabulary,
                                        item
                                )
                )

                .chain(() ->
                        exampleImporter
                                .importExamples(
                                        vocabulary,
                                        item
                                )
                )

                .replaceWith(
                        ImportStatus.UPDATED
                );
    }

    private enum ImportStatus {
        CREATED,
        UPDATED
    }
}