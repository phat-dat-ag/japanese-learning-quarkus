package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.VocabularyExample;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class VocabularyExampleRepository {

    public Uni<List<VocabularyExample>> findByVocabularyId(Long vocabularyId) {
        return Panache.getSession().flatMap(session -> session.createQuery("""
                        SELECT ve FROM VocabularyExample ve
                        JOIN FETCH ve.exampleSentence
                        WHERE ve.vocabularyId = :vocabularyId
                        """, VocabularyExample.class)
                .setParameter("vocabularyId", vocabularyId)
                .getResultList());
    }

    public Uni<Void> insert(
            Long vocabularyId,
            Long exampleSentenceId,
            String targetText,
            Integer displayOrder) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        INSERT INTO vocabulary_examples
                                            (
                                                vocabulary_id,
                                                example_sentence_id,
                                                target_text,
                                                display_order
                                            )
                                        VALUES
                                            (
                                                :vocabularyId,
                                                :exampleSentenceId,
                                                :targetText,
                                                :displayOrder
                                            )
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .setParameter("exampleSentenceId", exampleSentenceId)
                                .setParameter("targetText", targetText)
                                .setParameter("displayOrder", displayOrder)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }

    public Uni<VocabularyExample> findExampleAssignment(Long vocabularyId, Long exampleId) {
        return Panache.getSession()
                .flatMap(session -> session.createQuery("""
                                select assignment from VocabularyExample assignment
                                join fetch assignment.exampleSentence
                                where assignment.vocabularyId = :vocabularyId
                                  and assignment.exampleSentenceId = :exampleId
                                """, VocabularyExample.class)
                        .setParameter("vocabularyId", vocabularyId)
                        .setParameter("exampleId", exampleId)
                        .getSingleResultOrNull());
    }

    public Uni<Long> countOtherVocabularyAssignments(Long exampleId, Long vocabularyId) {
        return Panache.getSession()
                .flatMap(session -> session.createQuery("""
                                select count(assignment) from VocabularyExample assignment
                                where assignment.exampleSentenceId = :exampleId
                                  and assignment.vocabularyId <> :vocabularyId
                                """, Long.class)
                        .setParameter("exampleId", exampleId)
                        .setParameter("vocabularyId", vocabularyId)
                        .getSingleResult());
    }
}
