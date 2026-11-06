package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import com.japaneselearning.vocabulary.logging.LogVocabularyOperation;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.hibernate.exception.ConstraintViolationException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class LessonService {
    private static final Logger LOG = Logger.getLogger(LessonService.class);

    private static final Set<String> UNIQUE_CONSTRAINTS = Set.of(
            "uk_lessons_level_number",
            "uk_lessons_level_order"
    );

    private final LessonRepository lessonRepository;
    private final JlptLevelRepository jlptLevelRepository;

    public LessonService(
            LessonRepository lessonRepository,
            JlptLevelRepository jlptLevelRepository
    ) {
        this.lessonRepository = lessonRepository;
        this.jlptLevelRepository = jlptLevelRepository;
    }

    @WithSession
    @LogOperation(
            value = "lesson.detail",
            description = "Lesson metadata retrieval",
            level = Logger.Level.DEBUG
    )
    public Uni<LessonResponse> getLesson(Long lessonId) {
        return lessonRepository.findById(lessonId)
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException(
                        "RESOURCE_NOT_FOUND",
                        "Lesson not found with id: " + lessonId
                ))
                .map(this::toResponse);
    }

    @WithTransaction
    @LogOperation(
            value = "lesson.delete",
            description = "Lesson deletion"
    )
    public Uni<LessonResponse> deleteLesson(Long lessonId) {
        return lessonRepository.findByIdForUpdate(lessonId)
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException(
                        "RESOURCE_NOT_FOUND",
                        "Lesson not found with id: " + lessonId
                ))
                .chain(lesson -> lessonRepository.hasDeletionReferences(lessonId)
                        .invoke(referenced -> {
                            if (referenced) {
                                throw deletionConflict();
                            }
                        })
                        .chain(() -> lessonRepository.deleteIfUnreferenced(lessonId))
                        .invoke(deleted -> {
                            if (deleted != 1) {
                                throw deletionConflict();
                            }
                        })
                        .replaceWith(toResponse(lesson))
                )
                .onFailure(failure -> failure instanceof ConstraintViolationException violation
                        && violation.getSQLState() != null
                        && violation.getSQLState().startsWith("23")
                )
                .transform(failure -> deletionConflict());
    }

    private ConflictException deletionConflict() {
        return new ConflictException(
                "LESSON_CONFLICT",
                "Lesson is referenced by vocabulary assignments, "
                        + "quizzes, flashcards or learning history"
        );
    }

    @WithTransaction
    @LogVocabularyOperation(
            value = "lesson.create",
            description = "Lesson creation",
            successMessage = "Successfully created lesson with id={lessonId}"
    )
    public Uni<LessonResponse> createLesson(LessonWriteRequest request) {
        return createLessonInTransaction(request);
    }

    // Import items already own a transaction and its post-commit business log.
    Uni<LessonResponse> createLessonInTransaction(LessonWriteRequest request) {
        return flushAndMapConflicts(requireLevel(request.levelId())
                .call(() -> requireUniqueFields(null, request))
                .chain(() -> {
                    Lesson lesson = new Lesson();
                    lesson.createdAt = LocalDateTime.now();
                    applyRequest(lesson, request);
                    return lessonRepository.persist(lesson);
                })).map(this::toResponse);
    }

    @WithTransaction
    @LogVocabularyOperation(
            value = "lesson.update",
            description = "Lesson update",
            successMessage = "Successfully updated lesson with id={lessonId}"
    )
    public Uni<LessonResponse> updateLesson(
            Long lessonId,
            LessonWriteRequest request
    ) {
        return updateLessonInTransaction(lessonId, request);
    }

    Uni<LessonResponse> updateLessonInTransaction(
            Long lessonId,
            LessonWriteRequest request
    ) {
        return flushAndMapConflicts(lessonRepository.findByIdForUpdate(lessonId)
                .onItem()
                .ifNull()
                .failWith(() -> new ResourceNotFoundException(
                        "RESOURCE_NOT_FOUND", "Lesson not found with id: " + lessonId
                ))
                .call(() -> requireLevel(request.levelId()))
                .call(() -> requireUniqueFields(lessonId, request))
                .invoke(lesson -> applyRequest(lesson, request)))
                .map(this::toResponse);
    }

    private Uni<Void> requireLevel(Long levelId) {
        return jlptLevelRepository.findById(levelId)
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException(
                        "JLPT_LEVEL_NOT_FOUND",
                        "JLPT level not found with id: " + levelId
                ))
                .replaceWithVoid();
    }

    private Uni<Void> requireUniqueFields(
            Long lessonId,
            LessonWriteRequest request
    ) {
        return lessonRepository.findByLevelIdAndLessonNumber(
                        request.levelId(), request.lessonNumber()
                )
                .invoke(existing -> requireAvailable(
                        existing, lessonId, "Lesson number"
                ))
                .chain(() -> lessonRepository.findByLevelIdAndDisplayOrder(
                        request.levelId(), request.displayOrder()
                ))
                .invoke(existing -> requireAvailable(
                        existing, lessonId, "Display order"
                ))
                .replaceWithVoid();
    }

    private void requireAvailable(Lesson existing, Long lessonId, String field) {
        if (existing != null && !existing.id.equals(lessonId)) {
            throw new ConflictException(
                    "LESSON_CONFLICT",
                    field + " already exists in this JLPT level"
            );
        }
    }

    private Uni<Lesson> flushAndMapConflicts(Uni<Lesson> mutation) {
        return mutation.call(lessonRepository::flush)
                .onFailure(failure ->
                        failure instanceof ConstraintViolationException violation
                                && isLessonUniqueConstraint(violation.getConstraintName())
                )
                .transform(failure -> new ConflictException(
                        "LESSON_CONFLICT",
                        "Lesson number or display order already exists in this JLPT level"
                ));
    }

    private boolean isLessonUniqueConstraint(String name) {
        return name != null
                && UNIQUE_CONSTRAINTS
                .stream()
                .anyMatch(constraint ->
                        name.equals(constraint) || name.endsWith("." + constraint)
                );
    }

    private void applyRequest(Lesson lesson, LessonWriteRequest request) {
        lesson.levelId = request.levelId();
        lesson.lessonNumber = request.lessonNumber();
        lesson.title = request.title();
        lesson.description = request.description();
        lesson.displayOrder = request.displayOrder();
        lesson.updatedAt = LocalDateTime.now();
    }

    private LessonResponse toResponse(Lesson lesson) {
        return new LessonResponse(
                lesson.id, lesson.lessonNumber, lesson.title, lesson.description
        );
    }

    @WithSession
    @LogVocabularyOperation(
            value = "lesson.list",
            description = "Lesson retrieval by JLPT level",
            successMessage = "Successfully retrieved {count} lessons for the requested JLPT level"
    )
    public Uni<List<LessonResponse>> getLessonsByLevel(String level) {

        return jlptLevelRepository
                .findByCode(level)
                .onItem()
                .ifNull()
                .failWith(() ->
                        new ResourceNotFoundException(
                                "JLPT_LEVEL_NOT_FOUND",
                                "JLPT level not found"
                        )
                )
                .chain(jlptLevel ->
                        lessonRepository
                                .findByLevelId(jlptLevel.id)
                                .onSubscription().invoke(() -> StructuredLog.event(
                                        LOG,
                                        Logger.Level.DEBUG,
                                        "lesson.list.filters",
                                        "Retrieving lessons for the resolved JLPT level in display order",
                                        Map.of(
                                                "operation", "lesson.list",
                                                "levelId", jlptLevel.id
                                        )
                                ))
                )
                .map(lessons ->
                        lessons.stream()
                                .map(lesson ->
                                        new LessonResponse(
                                                lesson.id,
                                                lesson.lessonNumber,
                                                lesson.title,
                                                lesson.description
                                        )
                                )
                                .toList()
                );
    }
}
