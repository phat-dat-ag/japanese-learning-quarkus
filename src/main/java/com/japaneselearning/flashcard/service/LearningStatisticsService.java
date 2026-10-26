package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.dto.FlashcardPageResponse;
import com.japaneselearning.flashcard.dto.LearningStatisticsResponse;
import com.japaneselearning.flashcard.dto.PersonalFlashcardResponse;
import com.japaneselearning.flashcard.repository.FlashcardLearningRepository;
import com.japaneselearning.flashcard.repository.VocabularyBookmarkRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class LearningStatisticsService {
    private final FlashcardLearningRepository catalogue;
    private final VocabularyProgressService progress;
    private final VocabularyBookmarkRepository bookmarks;
    private final FlashcardTime time;

    public LearningStatisticsService(
            FlashcardLearningRepository catalogue,
            VocabularyProgressService progress,
            VocabularyBookmarkRepository bookmarks,
            FlashcardTime time
    ) {
        this.catalogue = catalogue;
        this.progress = progress;
        this.bookmarks = bookmarks;
        this.time = time;
    }

    @WithSession
    @LogOperation(value = "flashcard.statistics", id = "")
    public Uni<LearningStatisticsResponse> statistics(String subject, String level, Long lessonId) {
        String normalized = FlashcardValidation.level(level);

        return Panache.withTransaction(() -> {
            var now = FlashcardTime.stored(time.now());

            return catalogue.validateScope(normalized, lessonId)
                    .chain(() -> catalogue.progressCounts(subject, normalized, lessonId, now))
                    .chain(counts -> catalogue.reviewCounts(subject, normalized, lessonId).map(history -> {
                        long total = number(counts[0]);
                        long mastered = number(counts[4]);
                        long studied = number(history[0]);
                        long attempts = number(history[1]);
                        long forgotten = number(history[2]);
                        long scheduled = number(history[3]);

                        return new LearningStatisticsResponse(
                                total,
                                number(counts[1]),
                                number(counts[2]),
                                number(counts[3]),
                                mastered,
                                number(counts[5]),
                                studied,
                                attempts,
                                attempts - forgotten,
                                forgotten,
                                scheduled,
                                attempts - scheduled,
                                LearningStatisticsResponse.percentage(studied, total),
                                LearningStatisticsResponse.percentage(mastered, total)
                        );
                    }));
        });
    }

    @WithSession
    public Uni<FlashcardPageResponse<PersonalFlashcardResponse>> list(
            String subject,
            String level,
            Long lessonId,
            FlashcardLearningRepository.Collection collection,
            int page,
            int size
    ) {
        FlashcardValidation.pagination(page, size);

        String normalized = FlashcardValidation.level(level);

        return Panache.withTransaction(() -> {
            var now = FlashcardTime.stored(time.now());

            return catalogue.validateScope(normalized, lessonId)
                    .chain(() -> catalogue.cards(subject, normalized, lessonId, collection, now, page, size))
                    .chain(rows -> enrich(subject, rows))
                    .chain(cards ->
                            catalogue.countCards(subject, normalized, lessonId, collection, now)
                                    .map(total -> FlashcardPageResponse.of(cards, page, size, total))
                    );
        });
    }

    public Uni<List<PersonalFlashcardResponse>> enrich(String subject, List<Object[]> rows) {
        List<Long> ids = rows.stream().map(
                row -> ((Number) row[0]).longValue()
        ).toList();

        return progress.states(subject, ids)
                .chain(states -> bookmarks.findOwned(subject, ids)
                        .map(saved -> {
                            Set<Long> bookmarked = saved.stream()
                                    .map(row -> row.vocabularyId)
                                    .collect(Collectors.toSet());

                            return rows.stream().map(row -> {
                                Long id = ((Number) row[0]).longValue();

                                return new PersonalFlashcardResponse(
                                        id,
                                        (String) row[1],
                                        states.getOrDefault(id, ProgressState.unseen()),
                                        bookmarked.contains(id)
                                );
                            }).toList();
                        }));
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
