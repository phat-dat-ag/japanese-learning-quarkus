package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.VocabularyMeaning;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyMeaningRepository
        implements PanacheRepository<VocabularyMeaning> {

    public Uni<Void> deleteByVocabularyId(Long vocabularyId) {

        return delete(
                "vocabularyId",
                vocabularyId
        ).replaceWithVoid();
    }
}