package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.VocabularyPitchAccent;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyPitchAccentRepository
        implements PanacheRepository<VocabularyPitchAccent> {

    public Uni<Void> deleteByVocabularyReadingId(
            Long vocabularyReadingId) {

        return delete(
                "vocabularyReadingId",
                vocabularyReadingId
        ).replaceWithVoid();
    }
}