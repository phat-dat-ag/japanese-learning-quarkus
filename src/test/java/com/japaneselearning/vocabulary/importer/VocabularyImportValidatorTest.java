package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.importer.dto.VocabularyExampleImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyKanjiImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyMeaningImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyPitchAccentImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyReadingImportItem;
import com.japaneselearning.vocabulary.importer.dto.KanjiReadingImportItem;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VocabularyImportValidatorTest {

    private final VocabularyImportValidator validator =
            new VocabularyImportValidator();

    @Test
    void shouldAcceptValidVocabulary() {

        VocabularyImportItem item = new VocabularyImportItem();

        item.word = "学生";
        item.normalizedWord = "学生";

        item.levels = List.of("N5");

        // =========================
        // Readings
        // =========================

        VocabularyReadingImportItem reading =
                new VocabularyReadingImportItem();

        reading.reading = "がくせい";
        reading.isPrimary = true;
        reading.displayOrder = 1;

        item.readings = List.of(reading);

        // =========================
        // Meanings
        // =========================

        VocabularyMeaningImportItem viMeaning =
                new VocabularyMeaningImportItem();

        viMeaning.language = "vi";
        viMeaning.meaning = "học sinh";
        viMeaning.isPrimary = true;
        viMeaning.displayOrder = 1;

        VocabularyMeaningImportItem enMeaning =
                new VocabularyMeaningImportItem();

        enMeaning.language = "en";
        enMeaning.meaning = "student";
        enMeaning.isPrimary = true;
        enMeaning.displayOrder = 1;

        item.meanings = List.of(
                viMeaning,
                enMeaning
        );

        // =========================
        // Parts of Speech
        // =========================

        item.partsOfSpeech = List.of("NOUN");

        // =========================
        // Kanji
        // =========================

        VocabularyKanjiImportItem kanji =
                new VocabularyKanjiImportItem();

        kanji.character = "学";
        kanji.strokeCount = 8;
        kanji.meaningVi = "học";
        kanji.meaningEn = "study";

        KanjiReadingImportItem kanjiReading =
                new KanjiReadingImportItem();

        kanjiReading.reading = "ガク";
        kanjiReading.readingType = "ON";
        kanjiReading.displayOrder = 1;

        kanji.readings = List.of(kanjiReading);

        item.kanji = List.of(kanji);

        // =========================
        // Pitch Accent
        // =========================

        VocabularyPitchAccentImportItem pitchAccent =
                new VocabularyPitchAccentImportItem();

        pitchAccent.reading = "がくせい";
        pitchAccent.accentPattern = 2;

        item.pitchAccents = List.of(pitchAccent);

        // =========================
        // Example
        // =========================

        VocabularyExampleImportItem example =
                new VocabularyExampleImportItem();

        example.japaneseText = "私は学生です。";
        example.japaneseReading = "わたしはがくせいです。";
        example.meaningVi = "Tôi là học sinh.";
        example.meaningEn = "I am a student.";
        example.targetText = "学生";
        example.displayOrder = 1;

        item.examples = List.of(example);

        // =========================
        // Validate
        // =========================

        assertDoesNotThrow(
                () -> validator.validate(List.of(item))
        );
    }

    @Test
    void shouldRejectEmptyWord() {

        VocabularyImportItem item = new VocabularyImportItem();

        item.word = "";
        item.normalizedWord = "";

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(List.of(item))
        );
    }

    @Test
    void shouldRejectInvalidJlptLevel() {

        VocabularyImportItem item = new VocabularyImportItem();

        item.word = "学生";
        item.normalizedWord = "学生";
        item.levels = List.of("N6");

        assertThrows(
                IllegalArgumentException.class,
                () -> validator.validate(List.of(item))
        );
    }
}