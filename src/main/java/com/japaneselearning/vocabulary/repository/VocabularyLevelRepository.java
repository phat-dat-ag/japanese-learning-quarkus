package com.japaneselearning.vocabulary.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyLevelRepository {

    public Uni<Void> insert(
            Long vocabularyId,
            Long levelId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        INSERT INTO vocabulary_levels
                                            (vocabulary_id, level_id)
                                        VALUES
                                            (:vocabularyId, :levelId)
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .setParameter("levelId", levelId)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }

    public Uni<Void> deleteByVocabularyId(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        DELETE FROM vocabulary_levels
                                        WHERE vocabulary_id = :vocabularyId
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }
}