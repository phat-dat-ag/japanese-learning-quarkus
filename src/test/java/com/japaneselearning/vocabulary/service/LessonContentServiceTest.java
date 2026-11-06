package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.vocabulary.dto.content.LessonContentDocument;
import com.japaneselearning.vocabulary.dto.content.LessonContentWriteRequest;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.LessonContent;
import com.japaneselearning.vocabulary.repository.LessonContentRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LessonContentServiceTest {
    private final Contents contents = new Contents();
    private final LessonContentService service = new LessonContentService(new LessonRepository() {
        @Override
        public Uni<Lesson> findById(Long id) {
            return lesson(id);
        }

        @Override
        public Uni<Lesson> findByIdForUpdate(Long id) {
            return lesson(id);
        }

        private Uni<Lesson> lesson(Long id) {
            Lesson lesson = new Lesson();

            lesson.id = id;
            lesson.levelId = 1L;

            return Uni.createFrom().item(id == 99 ? null : lesson);
        }
    },
            contents,
            new LessonContentValidator()
    );

    @Test
    void createsUpdatesAndDeletesAtExplicitRevision() {
        var created = await(service.putContent(1L, request(0), null));

        assertEquals(1, created.version());
        assertEquals(created, await(service.getContent(1L)));

        var updated = await(service.putContent(1L, request(1), created.etag()));

        assertEquals(2, updated.version());
        assertEquals(created.createdAt(), updated.createdAt());
        assertEquals(updated, await(service.deleteContent(1L, 2, updated.etag())));
        assertNull(contents.row);
        assertEquals(
                "LESSON_CONTENT_NOT_FOUND",
                assertThrows(ResourceNotFoundException.class,
                        () -> await(service.getContent(1L))).getCode()
        );
    }

    @Test
    void rejectsMissingLessonsContentAndStalePreconditions() {
        assertEquals(
                "RESOURCE_NOT_FOUND",
                assertThrows(ResourceNotFoundException.class,
                        () -> await(service.getContent(99L))).getCode()
        );

        assertThrows(ResourceNotFoundException.class, () -> await(service.putContent(99L, request(0), null)));
        assertThrows(ResourceNotFoundException.class, () -> await(service.deleteContent(1L, 1, "missing")));
        assertThrows(ConflictException.class, () -> await(service.putContent(1L, request(1), "missing")));

        var created = await(service.putContent(1L, request(0), null));

        assertThrows(ConflictException.class, () -> await(service.putContent(1L, request(0), null)));
        assertThrows(ConflictException.class, () -> await(service.putContent(1L, request(1), null)));
        assertThrows(ConflictException.class, () -> await(service.deleteContent(1L, 2, created.etag())));

        assertEquals(1, contents.row.version);
    }

    private LessonContentWriteRequest request(long version) {
        return new LessonContentWriteRequest(1, version, new LessonContentDocument(List.of()));
    }

    private <T> T await(Uni<T> uni) {
        return uni.await().atMost(Duration.ofSeconds(2));
    }

    private static class Contents extends LessonContentRepository {
        LessonContent row;

        @Override
        public Uni<LessonContent> findById(Long id) {
            return Uni.createFrom().item(row);
        }

        @Override
        public Uni<LessonContent> persist(LessonContent entity) {
            row = entity;
            return Uni.createFrom().item(entity);
        }

        @Override
        public Uni<Void> delete(LessonContent entity) {
            row = null;
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Void> flush() {
            return Uni.createFrom().voidItem();
        }
    }
}
