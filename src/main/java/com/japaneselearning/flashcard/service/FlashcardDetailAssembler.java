package com.japaneselearning.flashcard.service;

import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.FlashcardExampleResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiReadingResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiResponse;
import com.japaneselearning.flashcard.dto.FlashcardLessonResponse;
import com.japaneselearning.flashcard.dto.FlashcardLevelResponse;
import com.japaneselearning.flashcard.dto.FlashcardMeaningResponse;
import com.japaneselearning.flashcard.dto.FlashcardPartOfSpeechResponse;
import com.japaneselearning.flashcard.dto.FlashcardReadingResponse;
import com.japaneselearning.flashcard.dto.VocabularyResponse;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Kanji;
import com.japaneselearning.vocabulary.entity.KanjiReading;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.PartOfSpeech;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.entity.VocabularyExample;
import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import com.japaneselearning.vocabulary.entity.VocabularyReading;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

final class FlashcardDetailAssembler {

    private final Vocabulary vocabulary;
    private List<VocabularyReading> readings;
    private List<VocabularyMeaning> meanings;
    private List<PartOfSpeech> partsOfSpeech;
    private List<JlptLevel> levels;
    private List<Lesson> lessons;
    private List<Kanji> kanji;
    private List<VocabularyExample> examples;
    private List<VocabularyPitchAccent> pitchAccents;
    private List<KanjiReading> kanjiReadings;

    FlashcardDetailAssembler(Vocabulary vocabulary) {
        this.vocabulary = vocabulary;
    }

    Long vocabularyId() {
        return vocabulary.id;
    }

    List<Long> readingIds() {
        return readings.stream().map(reading -> reading.id).toList();
    }

    List<Long> kanjiIds() {
        return kanji.stream().map(character -> character.id).toList();
    }

    void setReadings(List<VocabularyReading> readings) {
        this.readings = readings;
    }

    void setMeanings(List<VocabularyMeaning> meanings) {
        this.meanings = meanings;
    }

    void setPartsOfSpeech(List<PartOfSpeech> partsOfSpeech) {
        this.partsOfSpeech = partsOfSpeech;
    }

    void setLevels(List<JlptLevel> levels) {
        this.levels = levels;
    }

    void setLessons(List<Lesson> lessons) {
        this.lessons = lessons;
    }

    void setKanji(List<Kanji> kanji) {
        this.kanji = kanji;
    }

    void setExamples(List<VocabularyExample> examples) {
        this.examples = examples;
    }

    void setPitchAccents(List<VocabularyPitchAccent> pitchAccents) {
        this.pitchAccents = pitchAccents;
    }

    void setKanjiReadings(List<KanjiReading> kanjiReadings) {
        this.kanjiReadings = kanjiReadings;
    }

    FlashcardDetailResponse build() {
        Map<Long, List<Integer>> pitchAccentMap = pitchAccents.stream()
                .collect(Collectors.groupingBy(
                        accent -> accent.vocabularyReadingId,
                        Collectors.mapping(
                                accent -> accent.accentPattern,
                                Collectors.toList()
                        )
                ));

        Map<Long, List<FlashcardKanjiReadingResponse>> kanjiReadingMap =
                kanjiReadings.stream().collect(Collectors.groupingBy(
                        reading -> reading.kanjiId,
                        Collectors.mapping(
                                reading -> new FlashcardKanjiReadingResponse(
                                        reading.reading, reading.readingType
                                ),
                                Collectors.toList()
                        )
                ));

        return new FlashcardDetailResponse(
                new VocabularyResponse(
                        vocabulary.id, vocabulary.word, vocabulary.normalizedWord
                ),
                readings.stream().map(reading -> new FlashcardReadingResponse(
                        reading.reading,
                        reading.isPrimary,
                        pitchAccentMap.getOrDefault(reading.id, List.of())
                )).toList(),
                meanings.stream().map(meaning -> new FlashcardMeaningResponse(
                        meaning.languageCode, meaning.meaning, meaning.isPrimary
                )).toList(),
                partsOfSpeech.stream().map(pos -> new FlashcardPartOfSpeechResponse(
                        pos.code, pos.nameVi, pos.nameEn
                )).toList(),
                levels.stream().map(level -> new FlashcardLevelResponse(
                        level.code, level.name
                )).toList(),
                lessons.stream().map(lesson -> new FlashcardLessonResponse(
                        lesson.level.code,
                        lesson.level.name,
                        lesson.lessonNumber,
                        lesson.title,
                        lesson.description,
                        lesson.displayOrder
                )).toList(),
                kanji.stream().map(character -> new FlashcardKanjiResponse(
                        character.character,
                        character.strokeCount,
                        character.meaningVi,
                        character.meaningEn,
                        kanjiReadingMap.getOrDefault(character.id, List.of())
                )).toList(),
                examples.stream().map(example -> new FlashcardExampleResponse(
                        example.exampleSentence.japaneseText,
                        example.exampleSentence.japaneseReading,
                        example.exampleSentence.meaningVi,
                        example.exampleSentence.meaningEn,
                        example.targetText
                )).toList()
        );
    }
}
