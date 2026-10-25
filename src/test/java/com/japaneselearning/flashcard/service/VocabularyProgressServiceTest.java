package com.japaneselearning.flashcard.service;

import com.japaneselearning.flashcard.domain.LearningStatus;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.dto.LearningStatisticsResponse;
import com.japaneselearning.flashcard.entity.VocabularyProgress;
import com.japaneselearning.flashcard.repository.VocabularyProgressRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class VocabularyProgressServiceTest {
    @Test
    void absentProgressIsNewAndReadsDoNotCreateRows() {
        var repository = new MemoryProgress();
        var service = new VocabularyProgressService(repository);
        assertEquals(ProgressState.unseen(), VocabularyProgressService.state(null));
        assertTrue(service.states("owner", List.of(1L)).await().indefinitely().isEmpty());
        assertTrue(repository.rows.isEmpty());
    }

    @Test
    void usersHaveIndependentProgressForSameVocabulary() {
        var repository = new MemoryProgress();
        var service = new VocabularyProgressService(repository);
        var now = Instant.parse("2026-10-24T00:00:00Z");
        var learned = new ProgressState(LearningStatus.LEARNING, 1, 0, 86400, 2.5, now, now.plusSeconds(86400), null, 1);
        service.save("owner", 1L, null, learned, now).await().indefinitely();
        assertEquals(learned, service.states("owner", List.of(1L)).await().indefinitely().get(1L));
        assertTrue(service.states("other", List.of(1L)).await().indefinitely().isEmpty());
        assertEquals(1, repository.rows.size());
    }

    @Test
    void percentagesHandleEmptyPartialAndCompleteScopes() {
        assertEquals("0.00", LearningStatisticsResponse.percentage(0, 0).toString());
        assertEquals("33.33", LearningStatisticsResponse.percentage(1, 3).toString());
        assertEquals("100.00", LearningStatisticsResponse.percentage(3, 3).toString());
    }

    static class MemoryProgress extends VocabularyProgressRepository {
        final List<VocabularyProgress> rows = new ArrayList<>();

        @Override
        public Uni<List<VocabularyProgress>> findOwned(String subject, List<Long> ids) {
            return Uni.createFrom().item(rows.stream().filter(row -> row.userSubject.equals(subject) && ids.contains(row.vocabularyId)).toList());
        }

        @Override
        public Uni<VocabularyProgress> persist(VocabularyProgress entity) {
            rows.add(entity);
            return Uni.createFrom().item(entity);
        }
    }
}
