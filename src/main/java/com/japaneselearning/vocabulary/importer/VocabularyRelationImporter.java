package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyRelationImporter {

    private final VocabularyLevelImporter levelImporter;
    private final VocabularyReadingImporter readingImporter;
    private final VocabularyMeaningImporter meaningImporter;
    private final VocabularyPartOfSpeechImporter partOfSpeechImporter;
    private final VocabularyKanjiImporter kanjiImporter;
    private final VocabularyPitchAccentImporter pitchAccentImporter;
    private final VocabularyExampleImporter exampleImporter;
    private final VocabularyLessonImporter lessonImporter;

    public VocabularyRelationImporter(
            VocabularyLevelImporter levelImporter,
            VocabularyReadingImporter readingImporter,
            VocabularyMeaningImporter meaningImporter,
            VocabularyPartOfSpeechImporter partOfSpeechImporter,
            VocabularyKanjiImporter kanjiImporter,
            VocabularyPitchAccentImporter pitchAccentImporter,
            VocabularyExampleImporter exampleImporter,
            VocabularyLessonImporter lessonImporter
    ) {
        this.levelImporter = levelImporter;
        this.readingImporter = readingImporter;
        this.meaningImporter = meaningImporter;
        this.partOfSpeechImporter = partOfSpeechImporter;
        this.kanjiImporter = kanjiImporter;
        this.pitchAccentImporter = pitchAccentImporter;
        this.exampleImporter = exampleImporter;
        this.lessonImporter = lessonImporter;
    }

    public Uni<Void> importRelations(Vocabulary vocabulary, VocabularyImportItem item) {
        return levelImporter.importLevels(vocabulary, item)
                .chain(() -> lessonImporter.importLessons(vocabulary, item))
                .chain(() -> readingImporter.importReadings(vocabulary, item))
                .chain(() -> meaningImporter.importMeanings(vocabulary, item))
                .chain(() -> partOfSpeechImporter.importPartsOfSpeech(vocabulary, item))
                .chain(() -> kanjiImporter.importKanji(vocabulary, item))
                .chain(() -> pitchAccentImporter.importPitchAccents(vocabulary, item))
                .chain(() -> exampleImporter.importExamples(vocabulary, item));
    }

    public Uni<Void> validateLessons(List<VocabularyImportItem> items) {
        return Multi.createFrom().iterable(items)
                .onItem().transformToUniAndConcatenate(lessonImporter::validateLessons)
                .collect().asList()
                .replaceWithVoid();
    }

}
