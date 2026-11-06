package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.vocabulary.dto.content.LessonContentImportItem;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger.Level;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@ApplicationScoped
public class LessonContentImportPreflight {
    public record Prepared(
            int index,
            Long levelId,
            LessonContentImportItem item
    ) {
    }

    private final JlptLevelRepository levels;
    private final LessonRepository lessons;

    public LessonContentImportPreflight(
            JlptLevelRepository levels,
            LessonRepository lessons
    ) {
        this.levels = levels;
        this.lessons = lessons;
    }

    @WithSession
    @LogOperation(
            value = "lesson.content.import.validate",
            description = "Grammar import reference validation",
            level = Level.DEBUG
    )
    public Uni<List<Prepared>> validate(List<LessonContentImportItem> items) {
        // These sets belong to a subscription, never to the application-scoped bean.
        return Uni.createFrom().deferred(() -> {
            Set<String> keys = new HashSet<>();
            Set<String> orders = new HashSet<>();

            return Multi.createFrom()
                    .range(0, items.size())
                    .onItem()
                    .transformToUniAndConcatenate(index -> {
                        var item = items.get(index);

                        return levels.findByCode(item.level().trim())
                                .onItem().ifNull().failWith(() -> new ResourceNotFoundException(
                                        "JLPT_LEVEL_NOT_FOUND", "Import item " + index + " references an unknown JLPT level"
                                ))
                                .chain(level -> {
                                    if (!keys.add(level.id + ":" + item.lessonNumber())
                                            || !orders.add(level.id + ":" + item.displayOrder())
                                    ) {
                                        throw LessonContentValidator.invalid(
                                                "[" + index + "]", "Duplicate lesson key or display order within the level"
                                        );
                                    }

                                    return lessons.findByLevelIdAndDisplayOrder(level.id, item.displayOrder())
                                            .invoke(existing -> {
                                                if (existing != null && !existing.lessonNumber.equals(item.lessonNumber())) {
                                                    throw new ConflictException(
                                                            "LESSON_CONFLICT",
                                                            "Import item "
                                                                    + index
                                                                    + " display order is already assigned to a different lesson"
                                                    );
                                                }
                                            }).replaceWith(new Prepared(index, level.id, item));
                                });
                    }).collect().asList();
        });
    }
}
