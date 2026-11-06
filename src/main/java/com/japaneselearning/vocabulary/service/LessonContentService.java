package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument;
import com.japaneselearning.vocabulary.dto.content.LessonContentResponse;
import com.japaneselearning.vocabulary.dto.content.LessonContentWriteRequest;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.LessonContent;
import com.japaneselearning.vocabulary.repository.LessonContentRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Map;

@ApplicationScoped
public class LessonContentService {
    private final LessonRepository lessons;
    private final LessonContentRepository contents;
    private final LessonContentValidator validator;

    public LessonContentService(
            LessonRepository lessons,
            LessonContentRepository contents,
            LessonContentValidator validator
    ) {
        this.lessons = lessons;
        this.contents = contents;
        this.validator = validator;
    }

    @WithSession
    @LogOperation(
            value = "lesson.content.read",
            description = "Lesson content retrieval",
            level = Logger.Level.DEBUG
    )
    public Uni<LessonContentResponse> getContent(Long lessonId) {
        return requireLesson(lessonId, false)
                .chain(() -> requireContent(lessonId))
                .map(this::response);
    }

    @WithTransaction
    @LogOperation(
            value = "lesson.content.save",
            description = "Lesson content creation or replacement"
    )
    public Uni<LessonContentResponse> putContent(
            Long lessonId,
            LessonContentWriteRequest request,
            String ifMatch
    ) {
        return Uni.createFrom().voidItem().invoke(() -> validator.validate(request))
                .chain(() -> requireLesson(lessonId, true))
                .chain(lesson -> contents.findById(lessonId).chain(existing -> {
                    boolean creating = existing == null;

                    if (creating) {
                        if (request.expectedVersion() != 0 || ifMatch != null) {
                            throw conflict();
                        }

                        existing = new LessonContent();
                        existing.lessonId = lessonId;
                        existing.createdAt = now();
                        existing.version = 1;
                    } else {
                        requireRevision(existing, request.expectedVersion(), ifMatch);

                        if (existing.version == Long.MAX_VALUE) {
                            throw conflict();
                        }

                        existing.version++;
                    }

                    existing.schemaVersion = request.schemaVersion();
                    existing.content = LessonContentValidator.json().valueToTree(request.content());
                    existing.updatedAt = now();

                    LessonContent saved = existing;

                    Uni<LessonContent> mutation = creating
                            ? contents.persist(saved)
                            : Uni.createFrom().item(saved);

                    return mutation.call(contents::flush).chain(content -> OperationLog.outcome(
                            Uni.createFrom().item(response(content)),
                            creating ? "created" : "updated",
                            creating
                                    ? "Created structured grammar content for the lesson"
                                    : "Replaced structured grammar content for the lesson",
                            fields(lesson, content)
                    ));
                }));
    }

    @WithTransaction
    @LogOperation(
            value = "lesson.content.delete",
            description = "Lesson content deletion"
    )
    public Uni<LessonContentResponse> deleteContent(
            Long lessonId,
            long expectedVersion,
            String ifMatch
    ) {
        return requireLesson(lessonId, true)
                .chain(lesson -> requireContent(lessonId)
                        .chain(content -> {
                            requireRevision(content, expectedVersion, ifMatch);
                            LessonContentResponse deleted = response(content);

                            return contents.delete(content)
                                    .call(contents::flush)
                                    .chain(() -> OperationLog.outcome(
                                            Uni.createFrom().item(deleted),
                                            "completed",
                                            "Removed structured grammar content while preserving the lesson and assignments",
                                            fields(lesson, content)
                                    ));
                        })
                );
    }

    private Uni<Lesson> requireLesson(Long lessonId, boolean lock) {
        return (lock ? lessons.findByIdForUpdate(lessonId) : lessons.findById(lessonId))
                .onItem()
                .ifNull()
                .failWith(() -> new ResourceNotFoundException(
                        "RESOURCE_NOT_FOUND",
                        "Lesson not found with id: " + lessonId
                ));
    }

    private Uni<LessonContent> requireContent(Long lessonId) {
        return contents.findById(lessonId)
                .onItem()
                .ifNull()
                .failWith(() -> new ResourceNotFoundException(
                        "LESSON_CONTENT_NOT_FOUND",
                        "Lesson has no grammar content"
                ));
    }

    private void requireRevision(
            LessonContent content,
            long expectedVersion,
            String ifMatch
    ) {
        if (expectedVersion <= 0
                || content.version != expectedVersion
                || !LessonContentResponse.etag(content.lessonId, content.createdAt, content.version).equals(ifMatch)
        ) {
            throw conflict();
        }
    }

    private ConflictException conflict() {
        return new ConflictException(
                "LESSON_CONTENT_CONFLICT",
                "Content revision or If-Match does not match; retrieve the current document before editing"
        );
    }

    private LocalDateTime now() {
        return LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
    }

    private Map<String, Object> fields(Lesson lesson, LessonContent content) {
        return Map.of(
                "lessonId", lesson.id,
                "levelId", lesson.levelId,
                "schemaVersion", content.schemaVersion,
                "contentVersion", content.version
        );
    }

    private LessonContentResponse response(LessonContent content) {
        try {
            return new LessonContentResponse(
                    content.lessonId,
                    content.schemaVersion,
                    content.version,
                    LessonContentValidator.json().treeToValue(content.content, LessonContentDocument.class),
                    content.createdAt, content.updatedAt
            );
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Stored lesson content does not match its schema",
                    failure
            );
        }
    }
}
