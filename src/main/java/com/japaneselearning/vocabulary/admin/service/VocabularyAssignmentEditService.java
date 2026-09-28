package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.LessonAssignmentAdd;
import com.japaneselearning.vocabulary.admin.dto.LevelAssignmentAdd;
import com.japaneselearning.vocabulary.admin.dto.AssignmentOrderEdit;
import com.japaneselearning.vocabulary.admin.dto.PartOfSpeechAssignmentAdd;
import com.japaneselearning.vocabulary.admin.dto.VocabularyLevelResult;
import com.japaneselearning.vocabulary.admin.dto.VocabularyLessonResult;
import com.japaneselearning.vocabulary.admin.dto.VocabularyPartOfSpeechResult;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import com.japaneselearning.vocabulary.repository.LessonVocabularyRepository;
import com.japaneselearning.vocabulary.repository.PartOfSpeechRepository;
import com.japaneselearning.vocabulary.repository.VocabularyLevelRepository;
import com.japaneselearning.vocabulary.repository.VocabularyPartOfSpeechRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyAssignmentEditService {
    private final VocabularyEditPersistence persistence;
    private final JlptLevelRepository levels;
    private final LessonRepository lessons;
    private final PartOfSpeechRepository partsOfSpeech;
    private final VocabularyLevelRepository levelAssignments;
    private final LessonVocabularyRepository lessonAssignments;
    private final VocabularyPartOfSpeechRepository partOfSpeechAssignments;

    public VocabularyAssignmentEditService(
            VocabularyEditPersistence persistence,
            JlptLevelRepository levels,
            LessonRepository lessons,
            PartOfSpeechRepository partsOfSpeech,
            VocabularyLevelRepository levelAssignments,
            LessonVocabularyRepository lessonAssignments,
            VocabularyPartOfSpeechRepository partOfSpeechAssignments) {
        this.persistence = persistence;
        this.levels = levels;
        this.lessons = lessons;
        this.partsOfSpeech = partsOfSpeech;
        this.levelAssignments = levelAssignments;
        this.lessonAssignments = lessonAssignments;
        this.partOfSpeechAssignments = partOfSpeechAssignments;
    }

    @WithTransaction
    public Uni<List<VocabularyLevelResult>> addLevelAssignments(
            Long vocabularyId,
            List<LevelAssignmentAdd> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addLevelAssignment(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyLevelResult> updateLevelAssignmentOrder(
            Long vocabularyId,
            Long levelId,
            AssignmentOrderEdit request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireExistingAssignment(levelAssignments.existsLevelAssignment(vocabularyId, levelId)))
                .chain(() -> levelAssignments.updateLevelAssignmentOrder(vocabularyId, levelId, request.displayOrder()))
                .replaceWith(new VocabularyLevelResult(levelId)));
    }

    @WithTransaction
    public Uni<List<VocabularyLessonResult>> addLessonAssignments(
            Long vocabularyId,
            List<LessonAssignmentAdd> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(request -> addLessonAssignment(vocabularyId, request))
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyLessonResult> updateLessonAssignmentOrder(
            Long vocabularyId,
            Long lessonId,
            AssignmentOrderEdit request
    ) {
        if (request.displayOrder() < 1) {
            throw VocabularyEditPersistence.invalidRequest(
                    "displayOrder", "Lesson display order must be greater than zero");
        }
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireExistingAssignment(
                        lessonAssignments.existsLessonAssignment(vocabularyId, lessonId)))
                .chain(() -> lessonAssignments.updateLessonAssignmentOrder(
                        vocabularyId, lessonId, request.displayOrder()))
                .replaceWith(new VocabularyLessonResult(lessonId)));
    }

    @WithTransaction
    public Uni<List<VocabularyPartOfSpeechResult>> addPartOfSpeechAssignments(
            Long vocabularyId,
            List<PartOfSpeechAssignmentAdd> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addPartOfSpeechAssignment(vocabularyId, request))
                        .collect().asList()));
    }

    private Uni<VocabularyLevelResult> addLevelAssignment(Long vocabularyId, LevelAssignmentAdd request) {
        return persistence.requireFound(levels.findByCode(request.level()), "Level")
                .flatMap(level -> requireNewAssignment(levelAssignments.existsLevelAssignment(vocabularyId, level.id))
                        .chain(() -> levelAssignments.insert(vocabularyId, level.id, request.displayOrder()))
                        .replaceWith(new VocabularyLevelResult(level.id)));
    }

    private Uni<VocabularyLessonResult> addLessonAssignment(Long vocabularyId, LessonAssignmentAdd request) {
        return persistence.requireFound(lessons.findById(request.lessonId()), "Lesson")
                .flatMap(lesson -> requireLessonLevelAssignment(vocabularyId, lesson.levelId)
                        .chain(() -> requireNewAssignment(
                                lessonAssignments.existsLessonAssignment(vocabularyId, lesson.id)))
                        .chain(() -> lessonAssignments.insert(lesson.id, vocabularyId, request.displayOrder()))
                        .replaceWith(new VocabularyLessonResult(lesson.id)));
    }

    private Uni<VocabularyPartOfSpeechResult> addPartOfSpeechAssignment(
            Long vocabularyId,
            PartOfSpeechAssignmentAdd request
    ) {
        return persistence.requireFound(partsOfSpeech.findByCode(request.code()), "Part of speech")
                .flatMap(partOfSpeech -> requireNewAssignment(
                        partOfSpeechAssignments.existsPartOfSpeechAssignment(vocabularyId, partOfSpeech.id))
                        .chain(() -> partOfSpeechAssignments.insert(vocabularyId, partOfSpeech.id))
                        .replaceWith(new VocabularyPartOfSpeechResult(partOfSpeech.id)));
    }

    private Uni<Void> requireLessonLevelAssignment(Long vocabularyId, Long levelId) {
        return levelAssignments.existsLevelAssignment(vocabularyId, levelId).invoke(assigned -> {
            if (!assigned) {
                throw VocabularyEditPersistence.invalidRequest(
                        "lessonId", "Assign the lesson's JLPT level to the vocabulary first");
            }
        }).replaceWithVoid();
    }

    private Uni<Void> requireNewAssignment(Uni<Boolean> assignmentLookup) {
        return assignmentLookup.invoke(assigned -> {
            if (assigned) {
                throw VocabularyEditPersistence.conflict("Assignment already exists");
            }
        }).replaceWithVoid();
    }

    private Uni<Void> requireExistingAssignment(Uni<Boolean> assignmentLookup) {
        return assignmentLookup.invoke(assigned -> {
            if (!assigned) {
                throw VocabularyEditPersistence.resourceNotFound("Assignment");
            }
        }).replaceWithVoid();
    }
}
