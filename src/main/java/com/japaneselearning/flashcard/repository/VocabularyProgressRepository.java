package com.japaneselearning.flashcard.repository;

import com.japaneselearning.flashcard.entity.VocabularyProgress;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyProgressRepository implements PanacheRepository<VocabularyProgress> {
    public Uni<VocabularyProgress> findOwned(String subject, Long vocabularyId) {
        return find("userSubject = ?1 and vocabularyId = ?2", subject, vocabularyId).firstResult();
    }

    public Uni<List<VocabularyProgress>> findOwned(String subject, List<Long> ids) {
        if (ids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return list("userSubject = ?1 and vocabularyId in ?2", subject, ids);
    }
}
