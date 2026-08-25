package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.Vocabulary;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyRepository implements PanacheRepository<Vocabulary> {

    public Uni<Vocabulary> findByNormalizedWord(
            String normalizedWord) {

        return find("normalizedWord", normalizedWord)
                .firstResult();
    }
}