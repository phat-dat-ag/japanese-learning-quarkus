package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.FlashcardListItemResponse;
import com.japaneselearning.flashcard.dto.FlashcardListResponse;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;
import java.util.Map;

@ApplicationScoped
public class PersonalFlashcardService {
    private final FlashcardService cards;
    private final LearningStatisticsService learning;

    public PersonalFlashcardService(
            FlashcardService cards,
            LearningStatisticsService learning
    ) {
        this.cards = cards;
        this.learning = learning;
    }

    @WithSession
    @LogOperation(
            value = "flashcard.personal.list",
            description = "Personalized flashcard retrieval"
    )
    public Uni<FlashcardListResponse> list(
            String subject,
            String level,
            Integer lesson,
            int page,
            int size
    ) {
        return cards.getFlashcards(level, lesson, page, size)
                .chain(result -> learning.enrich(
                                        subject,
                                        result.flashcardItems().stream().map(card -> new Object[]{
                                                card.id(),
                                                card.word()
                                        }).toList()
                                )
                                .map(personal -> new FlashcardListResponse(
                                        personal.stream().map(card -> new FlashcardListItemResponse(
                                                card.id(),
                                                card.word(),
                                                card.progress(),
                                                card.bookmarked()
                                        )).toList(),
                                        result.page(),
                                        result.size(),
                                        result.totalElements(),
                                        result.totalPages()
                                ))
                ).chain(result -> OperationLog.outcome(
                        Uni.createFrom().item(result),
                        "completed",
                        "Successfully retrieved {count} personalized flashcards from {total} matching vocabulary entries",
                        Map.of(
                                "count", result.flashcardItems().size(),
                                "total", result.totalElements(),
                                "page", page,
                                "size", size
                        )
                ));
    }

    @WithSession
    @LogOperation(
            value = "flashcard.personal.detail",
            id = "vocabularyId",
            description = "Personalized flashcard detail retrieval"
    )
    public Uni<FlashcardDetailResponse> detail(String subject, Long id) {
        return cards.getFlashcard(id).chain(result -> learning.enrich(
                        subject,
                        List.<Object[]>of(new Object[]{
                                id,
                                result.vocabulary().word()
                        })
                ).map(personal -> {
                    var state = personal.get(0);

                    return new FlashcardDetailResponse(
                            result.vocabulary(),
                            result.readings(),
                            result.meanings(),
                            result.partsOfSpeech(),
                            result.levels(),
                            result.lessons(),
                            result.kanji(),
                            result.examples(),
                            state.progress(),
                            state.bookmarked()
                    );
                })
        ).chain(result -> OperationLog.outcome(
                Uni.createFrom().item(result),
                "completed",
                "Successfully retrieved vocabulary flashcard details with current learning progress and bookmark state",
                Map.of("vocabularyId", id)
        ));
    }
}
