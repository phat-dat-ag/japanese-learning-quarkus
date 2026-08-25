package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.VocabularyReading;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyReadingRepository
        implements PanacheRepository<VocabularyReading> {

    public Uni<VocabularyReading> findByVocabularyIdAndReading(
            Long vocabularyId,
            String reading) {

        return find(
                "vocabularyId = ?1 and reading = ?2",
                vocabularyId,
                reading
        ).firstResult();
    }

    public Uni<Void> deleteByVocabularyId(Long vocabularyId) {

        return delete(
                "vocabularyId",
                vocabularyId
        ).replaceWithVoid();
    }
}