package com.japaneselearning.vocabulary.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyLevelRepository {

    public Uni<List<String>> findLevelCodes(Long vocabularyId) {
        return Panache.getSession().flatMap(session -> session.createQuery("""
                        SELECT l.code FROM VocabularyLevel vl
                        JOIN JlptLevel l ON l.id = vl.levelId
                        WHERE vl.vocabularyId = :vocabularyId
                        """, String.class)
                .setParameter("vocabularyId", vocabularyId)
                .getResultList());
    }

    public Uni<Void> insert(
            Long vocabularyId,
            Long levelId,
            Integer displayOrder) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        INSERT INTO vocabulary_levels
                                            (vocabulary_id, level_id, display_order)
                                        VALUES
                                            (:vocabularyId, :levelId, :displayOrder)
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .setParameter("levelId", levelId)
                                .setParameter("displayOrder", displayOrder)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }

    public Uni<Boolean> existsLevelAssignment(Long vocabularyId, Long levelId) {
        return Panache.getSession()
                .flatMap(session -> session.createQuery("""
                                select count(assignment) from VocabularyLevel assignment
                                where assignment.vocabularyId = :vocabularyId
                                  and assignment.levelId = :levelId
                                """, Long.class)
                        .setParameter("vocabularyId", vocabularyId)
                        .setParameter("levelId", levelId)
                        .getSingleResult())
                .map(assignmentCount -> assignmentCount > 0);
    }

    public Uni<Void> updateLevelAssignmentOrder(Long vocabularyId, Long levelId, Integer displayOrder) {
        return Panache.getSession()
                .flatMap(session -> session.createMutationQuery("""
                                update VocabularyLevel set displayOrder = :displayOrder
                                where vocabularyId = :vocabularyId and levelId = :levelId
                                """)
                        .setParameter("displayOrder", displayOrder)
                        .setParameter("vocabularyId", vocabularyId)
                        .setParameter("levelId", levelId)
                        .executeUpdate())
                .replaceWithVoid();
    }
}
