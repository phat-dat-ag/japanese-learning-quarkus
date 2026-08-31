package com.japaneselearning.vocabulary.importer.dto;

import java.util.List;

public class VocabularyImportItem {

    public String word;

    public String normalizedWord;

    public List<String> levels;

    public List<LessonImportItem> lessons;

    public List<VocabularyReadingImportItem> readings;

    public List<VocabularyMeaningImportItem> meanings;

    public List<String> partsOfSpeech;

    public List<VocabularyKanjiImportItem> kanji;

    public List<VocabularyPitchAccentImportItem> pitchAccents;

    public List<VocabularyExampleImportItem> examples;
}