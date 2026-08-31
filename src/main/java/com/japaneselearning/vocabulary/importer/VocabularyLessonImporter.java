package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.LessonImportItem;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import com.japaneselearning.vocabulary.repository.LessonVocabularyRepository;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyLessonImporter {

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
                                importLesson(
                                        vocabulary,
                                        lessonItem
                                )
                )
                .collect()
                .asList()
                .replaceWithVoid();
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
                            .findByLevelIdAndLessonNumber(
                                    level.id,
                                    lessonItem.lessonNumber
                            )
                            .flatMap(lesson -> {

                                if (lesson == null) {
                                    return Uni.createFrom()
                                            .failure(
                                                    new IllegalArgumentException(
                                                            "Unknown lesson "
                                                                    + lessonItem.lessonNumber
                                                                    + " for JLPT level "
                                                                    + lessonItem.level
                                                                    + " and vocabulary: "
                                                                    + vocabulary.word
                                                    )
                                            );
                                }

                                return lessonVocabularyRepository
                                        .insert(
                                                lesson.id,
                                                vocabulary.id,
                                                lessonItem.displayOrder
                                        );
                            });
                });
    }
}