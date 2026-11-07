package com.japaneselearning.vocabulary.service;

import com.japaneselearning.vocabulary.dto.JlptLevelResponse;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JlptLevelServiceTest {
    @Test
    void returnsPersistedIdsAndPreservesRepositoryOrder() {
        JlptLevel first = level(37L, "N5", "Beginner");
        JlptLevel second = level(12L, "N4", "Elementary");
        var service = new JlptLevelService(new JlptLevelRepository() {
            @Override
            public Uni<List<JlptLevel>> findAllOrdered() {
                return Uni.createFrom().item(List.of(first, second));
            }
        });

        assertEquals(List.of(new JlptLevelResponse(37L, "N5", "Beginner"),
                        new JlptLevelResponse(12L, "N4", "Elementary")),
                service.getLevels().await().atMost(Duration.ofSeconds(2)));
    }

    private JlptLevel level(Long id, String code, String name) {
        JlptLevel level = new JlptLevel();
        level.id = id;
        level.code = code;
        level.name = name;
        return level;
    }
}
