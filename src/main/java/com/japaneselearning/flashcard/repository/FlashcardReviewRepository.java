package com.japaneselearning.flashcard.repository;

import com.japaneselearning.flashcard.entity.FlashcardReview;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class FlashcardReviewRepository implements PanacheRepository<FlashcardReview> {
    public Uni<FlashcardReview> findItem(Long itemId) {
        return find("sessionItemId", itemId).firstResult();
    }
}
