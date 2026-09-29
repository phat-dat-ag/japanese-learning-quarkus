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

    public Uni<Boolean> existsKanjiAssignment(Long vocabularyId, Long kanjiId) {
        return Panache.getSession()
                .flatMap(session -> session.createQuery("""
                                select count(assignment) from VocabularyKanji assignment
                                where assignment.vocabularyId = :vocabularyId
                                  and assignment.kanjiId = :kanjiId
                                """, Long.class)
                        .setParameter("vocabularyId", vocabularyId)
                        .setParameter("kanjiId", kanjiId)
                        .getSingleResult())
                .map(assignmentCount -> assignmentCount > 0);
    }

    public Uni<Void> updateKanjiAssignmentOrder(Long vocabularyId, Long kanjiId, Integer displayOrder) {
        return Panache.getSession()
                .flatMap(session -> session.createMutationQuery("""
                                update VocabularyKanji set displayOrder = :displayOrder
                                where vocabularyId = :vocabularyId and kanjiId = :kanjiId
                                """)
                        .setParameter("displayOrder", displayOrder)
                        .setParameter("vocabularyId", vocabularyId)
                        .setParameter("kanjiId", kanjiId)
                        .executeUpdate())
                .replaceWithVoid();
    }

    public Uni<Long> countOtherVocabularyAssignmentsForUpdate(Long kanjiId, Long vocabularyId) {
        // A current locking read avoids a stale REPEATABLE READ snapshot after waiting for the kanji lock.
        return Panache.getSession()
                .flatMap(session -> session.createNativeQuery("""
                                SELECT vocabulary_id FROM vocabulary_kanji
                                WHERE kanji_id = :kanjiId AND vocabulary_id <> :vocabularyId
                                FOR UPDATE
                                """, Long.class)
                        .setParameter("kanjiId", kanjiId)
                        .setParameter("vocabularyId", vocabularyId)
                        .getResultList())
                .map(assignments -> (long) assignments.size());
    }
}
