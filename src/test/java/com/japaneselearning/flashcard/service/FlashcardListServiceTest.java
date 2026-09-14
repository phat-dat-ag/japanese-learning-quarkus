package com.japaneselearning.flashcard.service;

import com.japaneselearning.flashcard.dto.FlashcardListItemResponse;
import com.japaneselearning.flashcard.dto.FlashcardListResponse;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FlashcardListServiceTest {

    private final StubRepository repository = new StubRepository();
    private final FlashcardService service = new FlashcardService();

    FlashcardListServiceTest() {
        service.flashcardRepository = repository;
    }

    @Test
    void mapsLevelResultsAndPreservesPaginationAndOrder() {
        repository.total = 5L;
        repository.results.complete(List.of(vocabulary(9L, "first"), vocabulary(3L, "second")));

        FlashcardListResponse response = service.getFlashcards(" n4 ", null, 1, 2)
                .await().atMost(Duration.ofSeconds(1));

        assertEquals(new FlashcardListResponse(
                List.of(new FlashcardListItemResponse(9L, "first"),
                        new FlashcardListItemResponse(3L, "second")),
                1, 2, 5L, 3
        ), response);
        assertEquals("N4", repository.level);
        assertEquals(null, repository.lesson);
        assertEquals(2, repository.offset);
        assertEquals(2, repository.limit);
        assertEquals(1, repository.countCalls);
    }

    @Test
    void usesLessonFilterForBothQueries() {
        repository.total = 4L;
        repository.results.complete(List.of(vocabulary(7L, "lesson-word")));

        FlashcardListResponse response = service.getFlashcards("n5", 3, 0, 2)
                .await().atMost(Duration.ofSeconds(1));

        assertEquals(new FlashcardListResponse(
                List.of(new FlashcardListItemResponse(7L, "lesson-word")), 0, 2, 4L, 2
        ), response);
        assertEquals("N5", repository.level);
        assertEquals(3, repository.lesson);
        assertEquals(0, repository.offset);
        assertEquals(2, repository.limit);
        assertEquals(1, repository.countCalls);
    }

    @Test
    void defaultsLevelAndReturnsEmptyResults() {
        repository.results.complete(List.of());

        assertEquals(new FlashcardListResponse(List.of(), 0, 20, 0L, 0),
                service.getFlashcards(null, null, 0, 20).await().atMost(Duration.ofSeconds(1)));
        assertEquals("N5", repository.level);
    }

    @Test
    void countsAnEmptyPageBeyondTheLastPage() {
        repository.total = 3L;
        repository.results.complete(List.of());

        assertEquals(new FlashcardListResponse(List.of(), 3, 2, 3L, 2),
                service.getFlashcards("N5", null, 3, 2).await().atMost(Duration.ofSeconds(1)));
    }

    @Test
    void waitsForTheListQueryBeforeStartingTheCount() {
        CompletableFuture<FlashcardListResponse> response =
                service.getFlashcards("N5", null, 0, 20).subscribeAsCompletionStage();

        assertEquals(0, repository.countCalls);
        assertFalse(response.isDone());

        repository.results.complete(List.of());

        assertEquals(1, repository.countCalls);
        assertEquals(0L, response.join().totalElements());
    }

    @Test
    void propagatesListFailureWithoutStartingTheCount() {
        IllegalStateException failure = new IllegalStateException("Query failed");
        repository.results.completeExceptionally(failure);

        assertSame(failure, assertThrows(IllegalStateException.class,
                () -> service.getFlashcards("N5", null, 0, 20)
                        .await().atMost(Duration.ofSeconds(1))));
        assertEquals(0, repository.countCalls);
    }

    private static Vocabulary vocabulary(Long id, String word) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.id = id;
        vocabulary.word = word;
        return vocabulary;
    }

    private static class StubRepository extends FlashcardRepository {
        final CompletableFuture<List<Vocabulary>> results = new CompletableFuture<>();
        String level;
        Integer lesson;
        int offset;
        int limit;
        int countCalls;
        long total;

        @Override
        public Uni<List<Vocabulary>> findVocabulary(
                String levelCode, Integer lessonNumber, int offset, int limit
        ) {
            this.level = levelCode;
            this.lesson = lessonNumber;
            this.offset = offset;
            this.limit = limit;
            return Uni.createFrom().completionStage(() -> results);
        }

        @Override
        public Uni<Long> countVocabulary(String levelCode, Integer lessonNumber) {
            assertEquals(level, levelCode);
            assertEquals(lesson, lessonNumber);
            countCalls++;
            return Uni.createFrom().item(total);
        }
    }
}
