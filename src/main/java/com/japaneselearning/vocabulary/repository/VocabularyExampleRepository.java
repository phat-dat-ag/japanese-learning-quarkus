package com.japaneselearning.vocabulary.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyExampleRepository {

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
                                .setParameter(
                                        "exampleSentenceId",
                                        exampleSentenceId
                                )
                                .setParameter("targetText", targetText)
                                .setParameter(
                                        "displayOrder",
                                        displayOrder
                                )
                                .executeUpdate()
                )
                .replaceWithVoid();
    }

    public Uni<Void> deleteByVocabularyId(Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        DELETE FROM vocabulary_examples
                                        WHERE vocabulary_id = :vocabularyId
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }
}