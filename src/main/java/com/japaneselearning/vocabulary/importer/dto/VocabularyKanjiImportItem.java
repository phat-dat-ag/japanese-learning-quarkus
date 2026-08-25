package com.japaneselearning.vocabulary.importer.dto;

import java.util.List;

public class VocabularyKanjiImportItem {

    public String character;

    public Integer strokeCount;

    public String meaningVi;

    public String meaningEn;

    public List<KanjiReadingImportItem> readings;
}