package com.japaneselearning.flashcard.service;

import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.FlashcardExampleResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiReadingResponse;
import com.japaneselearning.flashcard.dto.FlashcardKanjiResponse;
import com.japaneselearning.flashcard.dto.FlashcardLessonResponse;
import com.japaneselearning.flashcard.dto.FlashcardLevelResponse;
import com.japaneselearning.flashcard.dto.FlashcardMeaningResponse;
import com.japaneselearning.flashcard.dto.FlashcardPartOfSpeechResponse;
import com.japaneselearning.flashcard.dto.FlashcardPitchAccentResponse;
import com.japaneselearning.flashcard.dto.FlashcardReadingResponse;
import com.japaneselearning.flashcard.dto.VocabularyResponse;
import com.japaneselearning.flashcard.repository.FlashcardRepository.KanjiDetail;
import com.japaneselearning.flashcard.repository.FlashcardRepository.LessonDetail;
import com.japaneselearning.flashcard.repository.FlashcardRepository.LevelDetail;
import com.japaneselearning.vocabulary.entity.KanjiReading;
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
    private List<LevelDetail> levels;
    private List<LessonDetail> lessons;
    private List<KanjiDetail> kanji;
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
        return kanji.stream().map(detail -> detail.kanji().id).toList();
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

    void setLevels(List<LevelDetail> levels) {
        this.levels = levels;
    }

    void setLessons(List<LessonDetail> lessons) {
        this.lessons = lessons;
    }

    void setKanji(List<KanjiDetail> kanji) {
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
        Map<Long, List<FlashcardPitchAccentResponse>> pitchAccentMap = pitchAccents.stream()
                .collect(Collectors.groupingBy(
                        accent -> accent.vocabularyReadingId,
                        Collectors.mapping(
                                accent -> new FlashcardPitchAccentResponse(accent.id, accent.accentPattern),
                                Collectors.toList()
                        )
                ));

        Map<Long, List<FlashcardKanjiReadingResponse>> kanjiReadingMap =
                kanjiReadings.stream().collect(Collectors.groupingBy(
                        reading -> reading.kanjiId,
                        Collectors.mapping(
                                reading -> new FlashcardKanjiReadingResponse(
                                        reading.id, reading.reading, reading.readingType, reading.displayOrder
                                ),
                                Collectors.toList()
                        )
                ));

        return new FlashcardDetailResponse(
                new VocabularyResponse(
                        vocabulary.id, vocabulary.word, vocabulary.normalizedWord
                ),
                readings.stream().map(reading -> new FlashcardReadingResponse(
                        reading.id,
                        reading.reading,
                        reading.isPrimary,
                        reading.displayOrder,
                        pitchAccentMap.getOrDefault(reading.id, List.of())
                                .stream()
                                .map(FlashcardPitchAccentResponse::accentPattern)
                                .toList(),
                        pitchAccentMap.getOrDefault(reading.id, List.of())
                )).toList(),
                meanings.stream().map(meaning -> new FlashcardMeaningResponse(
                        meaning.id, meaning.languageCode, meaning.meaning, meaning.isPrimary, meaning.displayOrder
                )).toList(),
                partsOfSpeech.stream().map(pos -> new FlashcardPartOfSpeechResponse(
                        pos.code, pos.nameVi, pos.nameEn
                )).toList(),
                levels.stream().map(level -> new FlashcardLevelResponse(
                        level.level().id, level.level().code, level.level().name, level.displayOrder()
                )).toList(),
                lessons.stream().map(detail -> new FlashcardLessonResponse(
                        detail.lesson().id,
                        detail.lesson().level.code,
                        detail.lesson().level.name,
                        detail.lesson().lessonNumber,
                        detail.lesson().title,
                        detail.lesson().description,
                        detail.lesson().displayOrder,
                        detail.assignmentDisplayOrder()
                )).toList(),
                kanji.stream().map(detail -> new FlashcardKanjiResponse(
                        detail.kanji().id,
                        detail.kanji().character,
                        detail.kanji().strokeCount,
                        detail.kanji().meaningVi,
                        detail.kanji().meaningEn,
                        kanjiReadingMap.getOrDefault(detail.kanji().id, List.of()),
                        detail.displayOrder()
                )).toList(),
                examples.stream().map(example -> new FlashcardExampleResponse(
                        example.exampleSentenceId,
                        example.exampleSentence.japaneseText,
                        example.exampleSentence.japaneseReading,
                        example.exampleSentence.meaningVi,
                        example.exampleSentence.meaningEn,
                        example.targetText,
                        example.displayOrder
                )).toList()
        );
    }
}
