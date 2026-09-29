package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.ExampleSentence;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class ExampleSentenceRepository implements PanacheRepository<ExampleSentence> {
    public Uni<ExampleSentence> findSentenceByIdForUpdate(Long exampleId) {
        return find("id", exampleId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }
}
