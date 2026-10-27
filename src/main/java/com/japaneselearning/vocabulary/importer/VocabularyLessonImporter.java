package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.vocabulary.importer.dto.LessonImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import com.japaneselearning.vocabulary.repository.LessonVocabularyRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class VocabularyLessonImporter {
    private static final Logger LOG = Logger.getLogger(VocabularyLessonImporter.class);

    private final JlptLevelRepository jlptLevelRepository;
    private final LessonRepository lessonRepository;
    private final LessonVocabularyRepository lessonVocabularyRepository;

    public VocabularyLessonImporter(
            JlptLevelRepository jlptLevelRepository,
            LessonRepository lessonRepository,
            LessonVocabularyRepository lessonVocabularyRepository) {

        this.jlptLevelRepository = jlptLevelRepository;
        this.lessonRepository = lessonRepository;
        this.lessonVocabularyRepository =
                lessonVocabularyRepository;
    }

    public Uni<Void> importLessons(
            Vocabulary vocabulary,
            VocabularyImportItem item) {

        return Multi.createFrom()
                .iterable(item.lessons)
                .onItem()
                .transformToUniAndConcatenate(
                        lessonItem ->
                                importLesson(vocabulary, lessonItem)
                )
                .collect()
                .asList()
                .replaceWithVoid();
    }

    public Uni<Void> validateLessons(VocabularyImportItem item) {
        return Multi.createFrom().iterable(item.lessons)
                .onItem().transformToUniAndConcatenate(lessonItem ->
                        jlptLevelRepository.findByCode(lessonItem.level)
                                .flatMap(level -> level == null
                                        ? Uni.createFrom().failure(missingLesson(lessonItem))
                                        : lessonRepository
                                        .findByLevelIdAndLessonNumber(level.id, lessonItem.lessonNumber)
                                        .flatMap(lesson -> lesson == null
                                                ? Uni.createFrom().failure(missingLesson(lessonItem))
                                                : Uni.createFrom().voidItem())))
                .collect().asList()
                .replaceWithVoid();
    }

    private ValidationException missingLesson(LessonImportItem lessonItem) {
        StructuredLog.event(
                LOG,
                Logger.Level.DEBUG,
                "vocabulary.import.reference_missing",
                "Referenced lesson or JLPT level was not found; vocabulary imports require existing lessons",
                Map.of(
                        "operation", "vocabulary.import",
                        "reason", "lesson"
                )
        );

        return new ValidationException(
                "VALIDATION_ERROR",
                "Invalid vocabulary import",
                List.of(new ValidationError("lessons", "Unknown lesson "
                        + lessonItem.lessonNumber + " for JLPT level " + lessonItem.level)));
    }

    private Uni<Void> importLesson(
            Vocabulary vocabulary,
            LessonImportItem lessonItem) {

        return jlptLevelRepository
                .findByCode(lessonItem.level)
                .flatMap(level -> {
                    if (level == null) {
                        return Uni.createFrom()
                                .failure(
                                        new IllegalArgumentException(
                                                "Unknown JLPT level '"
                                                        + lessonItem.level
                                                        + "' for vocabulary: "
                                                        + vocabulary.word
                                        )
                                );
                    }

                    return lessonRepository
                            .findByLevelIdAndLessonNumber(level.id, lessonItem.lessonNumber)
                            .flatMap(lesson -> {
                                if (lesson == null) {
                                    return Uni.createFrom()
                                            .failure(
                                                    missingLesson(lessonItem)
                                            );
                                }

                                return lessonVocabularyRepository
                                        .insert(lesson.id, vocabulary.id, lessonItem.displayOrder);
                            });
                });
    }
}
