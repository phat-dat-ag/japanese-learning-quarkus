package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.vocabulary.dto.content.LessonContentImportResponse.*;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.Map;

@ApplicationScoped
public class LessonContentImportItemService {
    private final JlptLevelRepository levels;
    private final LessonRepository lessons;
    private final LessonService metadata;
    private final LessonContentService contents;

    public LessonContentImportItemService(
            JlptLevelRepository levels,
            LessonRepository lessons,
            LessonService metadata,
            LessonContentService contents
    ) {
        this.levels = levels;
        this.lessons = lessons;
        this.metadata = metadata;
        this.contents = contents;
    }

    @WithTransaction
    @LogOperation(
            value = "lesson.content.import.item",
            description = "Atomic grammar lesson import"
    )
    public Uni<Item> apply(
            LessonContentImportPreflight.Prepared prepared,
            Mode mode
    ) {
        var item = prepared.item();
        // Serializes missing-key creation across imports and coordinates FK inserts by ordinary creates.
        return levels.findByIdForUpdate(prepared.levelId())
                .onItem().ifNull().failWith(() -> new ResourceNotFoundException(
                        "JLPT_LEVEL_NOT_FOUND",
                        "JLPT level no longer exists"
                ))
                .chain(() -> lessons.findByLevelIdAndLessonNumber(
                        prepared.levelId(),
                        item.lessonNumber()
                ))
                .chain(existing -> {
                    if (existing != null && mode == Mode.SKIP_EXISTING) {
                        return result(prepared, Status.SKIPPED, existing.id);
                    }

                    var write = item.metadata(prepared.levelId());
                    boolean creating = existing == null;

                    return (creating
                            ? metadata.createLessonInTransaction(write)
                            : metadata.updateLessonInTransaction(existing.id, write)
                    )
                            .chain(lesson -> contents.putContent(
                                                    lesson.id(),
                                                    item.document(),
                                                    item.etag()
                                            )
                                            .chain(() -> result(
                                                    prepared,
                                                    creating ? Status.CREATED : Status.UPDATED, lesson.id()
                                            ))
                            );
                });
    }

    private Uni<Item> result(
            LessonContentImportPreflight.Prepared prepared,
            Status status,
            Long lessonId
    ) {
        return OperationLog.outcome(
                Uni.createFrom().item(
                        new Item(prepared.index(),
                                prepared.item().level().trim(),
                                prepared.item().lessonNumber(),
                                status,
                                lessonId,
                                null
                        )
                ),
                status.name().toLowerCase(java.util.Locale.ROOT),
                status == Status.SKIPPED
                        ? "Skipped an existing grammar lesson without changing metadata or content"
                        : "Saved lesson metadata and grammar content atomically",
                Map.of("lessonId", lessonId, "levelId", prepared.levelId()),
                status == Status.SKIPPED
                        ? org.jboss.logging.Logger.Level.DEBUG
                        : org.jboss.logging.Logger.Level.INFO
        );
    }
}
