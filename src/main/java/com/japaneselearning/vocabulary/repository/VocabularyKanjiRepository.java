package com.japaneselearning.vocabulary.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyKanjiRepository {

    public Uni<Void> insert(
            Long vocabularyId,
            Long kanjiId,
            Integer displayOrder) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        INSERT INTO vocabulary_kanji
                                            (
                                                vocabulary_id,
                                                kanji_id,
                                                display_order
                                            )
                                        VALUES
                                            (
                                                :vocabularyId,
                                                :kanjiId,
                                                :displayOrder
                                            )
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .setParameter("kanjiId", kanjiId)
                                .setParameter("displayOrder", displayOrder)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }

    public Uni<Void> deleteByVocabularyId(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        DELETE FROM vocabulary_kanji
                                        WHERE vocabulary_id = :vocabularyId
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }
}