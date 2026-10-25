package com.japaneselearning.flashcard.service;

import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.FlashcardListItemResponse;
import com.japaneselearning.flashcard.dto.FlashcardListResponse;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

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
                );
    }

    @WithSession
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
        );
    }
}
