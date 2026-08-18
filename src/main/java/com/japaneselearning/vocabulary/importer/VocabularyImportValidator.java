package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.importer.dto.ExampleSentenceImport;
import com.japaneselearning.vocabulary.importer.dto.KanjiImport;
import com.japaneselearning.vocabulary.importer.dto.PitchAccentImport;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyMeaningImport;
import com.japaneselearning.vocabulary.importer.dto.VocabularyReadingImport;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class VocabularyImportValidator {

    private static final Set<String> SUPPORTED_LEVELS = Set.of(
            "N1",
            "N2",
            "N3",
            "N4",
            "N5"
    );

    private static final Set<String> SUPPORTED_LANGUAGES = Set.of(
            "vi",
            "en"
    );

    public void validate(List<VocabularyImportItem> items) {

        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vocabulary data must not be empty"
            );
        }

        Set<String> words = new HashSet<>();

        for (VocabularyImportItem item : items) {
            validateItem(item);

            if (!words.add(item.word)) {
                throw new IllegalArgumentException(
                        "Duplicate vocabulary in import file: "
                                + item.word
                );
            }
        }
    }

    private void validateItem(VocabularyImportItem item) {

        if (item == null) {
            throw new IllegalArgumentException(
                    "Vocabulary item must not be null"
            );
        }

        if (isBlank(item.word)) {
            throw new IllegalArgumentException(
                    "Vocabulary word must not be blank"
            );
        }

        if (isBlank(item.normalizedWord)) {
            throw new IllegalArgumentException(
                    "Normalized word must not be blank: "
                            + item.word
            );
        }

        validateLevels(item);

        validateReadings(item);

        validateMeanings(item);

        validatePartsOfSpeech(item);

        validateKanji(item);

        validatePitchAccents(item);

        validateExamples(item);
    }

    private void validateLevels(VocabularyImportItem item) {

        if (item.levels == null || item.levels.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vocabulary must have at least one JLPT level: "
                            + item.word
            );
        }

        for (String level : item.levels) {

            if (!SUPPORTED_LEVELS.contains(level)) {
                throw new IllegalArgumentException(
                        "Unsupported JLPT level '"
                                + level
                                + "' for vocabulary: "
                                + item.word
                );
            }
        }
    }

    private void validateReadings(VocabularyImportItem item) {

        if (item.readings == null || item.readings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vocabulary must have at least one reading: "
                            + item.word
            );
        }

        boolean hasPrimaryReading = false;

        for (VocabularyReadingImport reading : item.readings) {

            if (reading == null || isBlank(reading.reading)) {
                throw new IllegalArgumentException(
                        "Reading must not be blank: "
                                + item.word
                );
            }

            if (reading.isPrimary) {
                hasPrimaryReading = true;
            }
        }

        if (!hasPrimaryReading) {
            throw new IllegalArgumentException(
                    "Vocabulary must have a primary reading: "
                            + item.word
            );
        }
    }

    private void validateMeanings(VocabularyImportItem item) {

        if (item.meanings == null || item.meanings.isEmpty()) {
            throw new IllegalArgumentException(
                    "Vocabulary must have at least one meaning: "
                            + item.word
            );
        }

        for (VocabularyMeaningImport meaning : item.meanings) {

            if (meaning == null) {
                throw new IllegalArgumentException(
                        "Meaning must not be null: "
                                + item.word
                );
            }

            if (!SUPPORTED_LANGUAGES.contains(
                    meaning.language
            )) {
                throw new IllegalArgumentException(
                        "Unsupported meaning language '"
                                + meaning.language
                                + "' for vocabulary: "
                                + item.word
                );
            }

            if (isBlank(meaning.meaning)) {
                throw new IllegalArgumentException(
                        "Meaning must not be blank: "
                                + item.word
                );
            }
        }
    }

    private void validatePartsOfSpeech(
            VocabularyImportItem item) {

        if (item.partsOfSpeech == null ||
                item.partsOfSpeech.isEmpty()) {

            throw new IllegalArgumentException(
                    "Vocabulary must have at least one part of speech: "
                            + item.word
            );
        }
    }

    private void validateKanji(
            VocabularyImportItem item) {

        if (item.kanji == null) {
            return;
        }

        for (KanjiImport kanji : item.kanji) {

            if (kanji == null ||
                    isBlank(kanji.character)) {

                throw new IllegalArgumentException(
                        "Kanji character must not be blank: "
                                + item.word
                );
            }

            if (kanji.readings == null ||
                    kanji.readings.isEmpty()) {

                throw new IllegalArgumentException(
                        "Kanji must have at least one reading: "
                                + kanji.character
                );
            }
        }
    }

    private void validatePitchAccents(
            VocabularyImportItem item) {

        if (item.pitchAccents == null) {
            return;
        }

        for (PitchAccentImport accent : item.pitchAccents) {

            if (accent == null ||
                    isBlank(accent.reading)) {

                throw new IllegalArgumentException(
                        "Pitch accent reading must not be blank: "
                                + item.word
                );
            }

            if (accent.accentPattern < 0) {

                throw new IllegalArgumentException(
                        "Pitch accent pattern must not be negative: "
                                + item.word
                );
            }
        }
    }

    private void validateExamples(
            VocabularyImportItem item) {

        if (item.examples == null ||
                item.examples.isEmpty()) {

            throw new IllegalArgumentException(
                    "Vocabulary must have at least one example: "
                            + item.word
            );
        }

        for (ExampleSentenceImport example : item.examples) {

            if (example == null) {
                throw new IllegalArgumentException(
                        "Example sentence must not be null: "
                                + item.word
                );
            }

            if (isBlank(example.japaneseText)) {
                throw new IllegalArgumentException(
                        "Japanese example must not be blank: "
                                + item.word
                );
            }

            if (isBlank(example.japaneseReading)) {
                throw new IllegalArgumentException(
                        "Japanese reading must not be blank: "
                                + item.word
                );
            }

            if (isBlank(example.meaningVi)) {
                throw new IllegalArgumentException(
                        "Vietnamese meaning must not be blank: "
                                + item.word
                );
            }

            if (isBlank(example.meaningEn)) {
                throw new IllegalArgumentException(
                        "English meaning must not be blank: "
                                + item.word
                );
            }

            if (isBlank(example.targetText)) {
                throw new IllegalArgumentException(
                        "Target text must not be blank: "
                                + item.word
                );
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}