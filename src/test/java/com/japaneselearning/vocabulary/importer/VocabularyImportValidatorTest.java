package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.importer.dto.KanjiImport;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyMeaningImport;
import com.japaneselearning.vocabulary.importer.dto.VocabularyReadingImport;
import com.japaneselearning.vocabulary.importer.dto.KanjiReadingImport;
import com.japaneselearning.vocabulary.importer.dto.PitchAccentImport;
import com.japaneselearning.vocabulary.importer.dto.ExampleSentenceImport;

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

        VocabularyReadingImport reading =
                new VocabularyReadingImport();

        reading.reading = "がくせい";
        reading.isPrimary = true;
        reading.displayOrder = 1;

        item.readings = List.of(reading);

        VocabularyMeaningImport viMeaning =
                new VocabularyMeaningImport();

        viMeaning.language = "vi";
        viMeaning.meaning = "học sinh";
        viMeaning.isPrimary = true;
        viMeaning.displayOrder = 1;

        VocabularyMeaningImport enMeaning =
                new VocabularyMeaningImport();

        enMeaning.language = "en";
        enMeaning.meaning = "student";
        enMeaning.isPrimary = true;
        enMeaning.displayOrder = 1;

        item.meanings = List.of(
                viMeaning,
                enMeaning
        );

        item.partsOfSpeech = List.of("NOUN");

        KanjiImport kanji = new KanjiImport();

        kanji.character = "学";
        kanji.strokeCount = 8;
        kanji.meaningVi = "học";
        kanji.meaningEn = "study";

        KanjiReadingImport kanjiReading =
                new KanjiReadingImport();

        kanjiReading.reading = "ガク";
        kanjiReading.readingType = "ON";
        kanjiReading.displayOrder = 1;

        kanji.readings = List.of(kanjiReading);

        item.kanji = List.of(kanji);

        PitchAccentImport pitchAccent =
                new PitchAccentImport();

        pitchAccent.reading = "がくせい";
        pitchAccent.accentPattern = 2;

        item.pitchAccents = List.of(pitchAccent);

        ExampleSentenceImport example =
                new ExampleSentenceImport();

        example.japaneseText = "私は学生です。";
        example.japaneseReading = "わたしはがくせいです。";
        example.meaningVi = "Tôi là học sinh.";
        example.meaningEn = "I am a student.";
        example.targetText = "学生";
        example.displayOrder = 1;

        item.examples = List.of(example);

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