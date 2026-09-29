package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.vocabulary.admin.dto.LessonAssignmentAddRequest;
import com.japaneselearning.vocabulary.admin.dto.LevelAssignmentAddRequest;
import com.japaneselearning.vocabulary.admin.dto.AssignmentOrderUpdateRequest;
import com.japaneselearning.vocabulary.admin.dto.PartOfSpeechAssignmentAddRequest;
import com.japaneselearning.vocabulary.admin.dto.VocabularyLevelResponse;
import com.japaneselearning.vocabulary.admin.dto.VocabularyLessonResponse;
import com.japaneselearning.vocabulary.admin.dto.VocabularyPartOfSpeechResponse;
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
            VocabularyPartOfSpeechRepository partOfSpeechAssignments
    ) {
        this.persistence = persistence;
        this.levels = levels;
        this.lessons = lessons;
        this.partsOfSpeech = partsOfSpeech;
        this.levelAssignments = levelAssignments;
        this.lessonAssignments = lessonAssignments;
        this.partOfSpeechAssignments = partOfSpeechAssignments;
    }

    @WithTransaction
    public Uni<List<VocabularyLevelResponse>> addLevelAssignments(
            Long vocabularyId,
            List<LevelAssignmentAddRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addLevelAssignment(vocabularyId, request)
                        )
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyLevelResponse> updateLevelAssignmentOrder(
            Long vocabularyId,
            Long levelId,
            AssignmentOrderUpdateRequest request
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireExistingAssignment(
                        levelAssignments.existsLevelAssignment(vocabularyId, levelId), levelId
                ))
                .chain(() -> levelAssignments.updateLevelAssignmentOrder(
                        vocabularyId, levelId, request.displayOrder()
                ))
                .replaceWith(new VocabularyLevelResponse(levelId))
        );
    }

    @WithTransaction
    public Uni<List<VocabularyLessonResponse>> addLessonAssignments(
            Long vocabularyId,
            List<LessonAssignmentAddRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addLessonAssignment(vocabularyId, request)
                        )
                        .collect().asList()));
    }

    @WithTransaction
    public Uni<VocabularyLessonResponse> updateLessonAssignmentOrder(
            Long vocabularyId,
            Long lessonId,
            AssignmentOrderUpdateRequest request
    ) {
        if (request.displayOrder() < 1) {
            throw VocabularyEditPersistence.invalidRequest(
                    "displayOrder", "Lesson display order must be greater than zero");
        }

        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> requireExistingAssignment(
                        lessonAssignments.existsLessonAssignment(vocabularyId, lessonId), lessonId
                ))
                .chain(() -> lessonAssignments.updateLessonAssignmentOrder(
                        vocabularyId, lessonId, request.displayOrder()
                ))
                .replaceWith(new VocabularyLessonResponse(lessonId)));
    }

    @WithTransaction
    public Uni<List<VocabularyPartOfSpeechResponse>> addPartOfSpeechAssignments(
            Long vocabularyId,
            List<PartOfSpeechAssignmentAddRequest> requests
    ) {
        return persistence.flushAndMapUniqueConflicts(persistence.requireVocabularyForUpdate(vocabularyId)
                .chain(() -> Multi.createFrom().iterable(requests)
                        .onItem().transformToUniAndConcatenate(
                                request -> addPartOfSpeechAssignment(vocabularyId, request)
                        )
                        .collect().asList()
                )
        );
    }

    private Uni<VocabularyLevelResponse> addLevelAssignment(
            Long vocabularyId,
            LevelAssignmentAddRequest request
    ) {
        return persistence.requireFound(levels.findByCode(request.level()), "Level", request.level())
                .flatMap(level -> requireNewAssignment(levelAssignments.existsLevelAssignment(vocabularyId, level.id))
                        .chain(() -> levelAssignments.insert(vocabularyId, level.id, request.displayOrder()))
                        .replaceWith(new VocabularyLevelResponse(level.id))
                );
    }

    private Uni<VocabularyLessonResponse> addLessonAssignment(
            Long vocabularyId,
            LessonAssignmentAddRequest request
    ) {
        return persistence.requireFound(lessons.findById(request.lessonId()), "Lesson", request.lessonId())
                .flatMap(lesson -> requireLessonLevelAssignment(vocabularyId, lesson.levelId)
                        .chain(() -> requireNewAssignment(
                                lessonAssignments.existsLessonAssignment(vocabularyId, lesson.id)
                        ))
                        .chain(() -> lessonAssignments.insert(lesson.id, vocabularyId, request.displayOrder()))
                        .replaceWith(new VocabularyLessonResponse(lesson.id))
                );
    }

    private Uni<VocabularyPartOfSpeechResponse> addPartOfSpeechAssignment(
            Long vocabularyId,
            PartOfSpeechAssignmentAddRequest request
    ) {
        return persistence.requireFound(partsOfSpeech.findByCode(request.code()), "Part of speech", request.code())
                .flatMap(partOfSpeech -> requireNewAssignment(
                        partOfSpeechAssignments.existsPartOfSpeechAssignment(vocabularyId, partOfSpeech.id)
                )
                        .chain(() -> partOfSpeechAssignments.insert(vocabularyId, partOfSpeech.id))
                        .replaceWith(new VocabularyPartOfSpeechResponse(partOfSpeech.id)));
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

    private Uni<Void> requireExistingAssignment(Uni<Boolean> assignmentLookup, Long resourceId) {
        return assignmentLookup.invoke(assigned -> {
            if (!assigned) {
                throw VocabularyEditPersistence.resourceNotFound("Assignment", resourceId);
            }
        }).replaceWithVoid();
    }
}
