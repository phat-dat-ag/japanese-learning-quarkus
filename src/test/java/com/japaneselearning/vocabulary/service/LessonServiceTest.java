package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import io.smallrye.mutiny.Uni;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LessonServiceTest {
    private final Lessons lessons = new Lessons();
    private final LessonService service = new LessonService(lessons, new JlptLevelRepository() {
        @Override
        public Uni<JlptLevel> findById(Long id) {
            JlptLevel level = new JlptLevel();
            level.id = id;
            return Uni.createFrom().item(id == 99L ? null : level);
        }
    });

    @Test
    void createsAllFieldsAndTimestamps() {
        var result = await(service.createLesson(request(1L, 1, 1)));
        Lesson saved = lessons.rows.get(0);
        assertEquals(saved.id, result.id());
        assertEquals(1L, saved.levelId);
        assertEquals(1, saved.lessonNumber);
        assertEquals("Title", saved.title);
        assertEquals("Description", saved.description);
        assertEquals(1, saved.displayOrder);
        assertNotNull(saved.createdAt);
        assertNotNull(saved.updatedAt);
        assertEquals(1, lessons.flushes);
    }

    @Test
    void updatesAllMutableFieldsAndAllowsUnchangedUniqueKeys() {
        var id = await(service.createLesson(request(1L, 1, 1))).id();
        await(service.updateLesson(id, request(1L, 1, 1)));
        var createdAt = lessons.rows.get(0).createdAt;
        var result = await(service.updateLesson(id, new LessonWriteRequest(2L, 2, "Changed", null, 3)));
        Lesson saved = lessons.rows.get(0);
        assertEquals(id, result.id());
        assertEquals(2L, saved.levelId);
        assertEquals(2, saved.lessonNumber);
        assertEquals("Changed", result.title());
        assertNull(result.description());
        assertEquals(3, saved.displayOrder);
        assertEquals(createdAt, saved.createdAt);
        assertEquals(1, lessons.rows.size());
    }

    @Test
    void rejectsMissingLessonAndLevelBeforeMutation() {
        assertThrows(ResourceNotFoundException.class,
                () -> await(service.updateLesson(99L, request(1L, 1, 1))));
        assertThrows(ResourceNotFoundException.class,
                () -> await(service.createLesson(request(99L, 1, 1))));
        var id = await(service.createLesson(request(1L, 1, 1))).id();
        assertThrows(ResourceNotFoundException.class,
                () -> await(service.updateLesson(id, request(99L, 2, 2))));
        assertEquals(1L, lessons.rows.get(0).levelId);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void rejectsDuplicateNumberOrOrderOnCreateAndUpdate(boolean duplicateNumber) {
        await(service.createLesson(request(1L, 1, 1)));
        var second = await(service.createLesson(request(2L, 2, 2))).id();
        var duplicate = request(1L, duplicateNumber ? 1 : 2, duplicateNumber ? 2 : 1);
        assertThrows(ConflictException.class, () -> await(service.createLesson(duplicate)));
        assertThrows(ConflictException.class, () -> await(service.updateLesson(second, duplicate)));
        assertEquals(2L, lessons.rows.get(1).levelId);
        assertEquals(2, lessons.rows.size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"uk_lessons_level_number", "uk_lessons_level_order",
            "lessons.uk_lessons_level_number", "lessons.uk_lessons_level_order"})
    void mapsDatabaseUniqueRacesOnBothMutations(String constraint) {
        var id = await(service.createLesson(request(1L, 1, 1))).id();
        lessons.flushFailure = new ConstraintViolationException("private SQL", new SQLException(), constraint);
        assertEquals("LESSON_CONFLICT", assertThrows(ConflictException.class,
                () -> await(service.createLesson(request(2L, 2, 2)))).getCode());
        assertThrows(ConflictException.class, () -> await(service.updateLesson(id, request(1L, 1, 1))));
    }

    @Test
    void doesNotMislabelOtherDatabaseFailures() {
        lessons.flushFailure = new ConstraintViolationException("failure", new SQLException(), "fk_lessons_level");
        assertSame(lessons.flushFailure, assertThrows(ConstraintViolationException.class,
                () -> await(service.createLesson(request(1L, 1, 1)))));
    }

    private LessonWriteRequest request(Long levelId, int number, int order) {
        return new LessonWriteRequest(levelId, number, "Title", "Description", order);
    }

    private <T> T await(Uni<T> operation) {
        return operation.await().atMost(Duration.ofSeconds(2));
    }

    private static class Lessons extends LessonRepository {
        final List<Lesson> rows = new ArrayList<>();
        int flushes;
        RuntimeException flushFailure;

        @Override
        public Uni<Lesson> findByIdForUpdate(Long id) {
            return Uni.createFrom().item(rows.stream().filter(row -> row.id.equals(id)).findFirst().orElse(null));
        }

        @Override
        public Uni<Lesson> findByLevelIdAndLessonNumber(Long level, Integer number) {
            return Uni.createFrom().item(rows.stream()
                    .filter(row -> row.levelId.equals(level) && row.lessonNumber.equals(number))
                    .findFirst().orElse(null));
        }

        @Override
        public Uni<Lesson> findByLevelIdAndDisplayOrder(Long level, Integer order) {
            return Uni.createFrom().item(rows.stream()
                    .filter(row -> row.levelId.equals(level) && row.displayOrder.equals(order))
                    .findFirst().orElse(null));
        }

        @Override
        public Uni<Lesson> persist(Lesson lesson) {
            lesson.id = (long) rows.size() + 1;
            rows.add(lesson);
            return Uni.createFrom().item(lesson);
        }

        @Override
        public Uni<Void> flush() {
            flushes++;
            return flushFailure == null ? Uni.createFrom().voidItem() : Uni.createFrom().failure(flushFailure);
        }
    }
}
