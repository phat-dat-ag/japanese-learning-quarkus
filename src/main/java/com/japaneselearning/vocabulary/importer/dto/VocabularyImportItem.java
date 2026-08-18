package com.japaneselearning.vocabulary.importer.dto;

import java.util.List;

public class VocabularyImportItem {

    public String word;

    public String normalizedWord;

    public List<String> levels;

    public List<VocabularyReadingImport> readings;

    public List<VocabularyMeaningImport> meanings;

    public List<String> partsOfSpeech;

    public List<KanjiImport> kanji;

    public List<PitchAccentImport> pitchAccents;

    public List<ExampleSentenceImport> examples;
}