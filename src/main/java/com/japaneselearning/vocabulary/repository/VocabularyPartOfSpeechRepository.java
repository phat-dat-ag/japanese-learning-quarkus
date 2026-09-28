package com.japaneselearning.vocabulary.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VocabularyPartOfSpeechRepository {

    public Uni<Void> insert(Long vocabularyId, Long partOfSpeechId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        INSERT INTO vocabulary_parts_of_speech
                                            (vocabulary_id, part_of_speech_id)
                                        VALUES
                                            (:vocabularyId, :partOfSpeechId)
                                        """)
                                .setParameter("vocabularyId", vocabularyId)
                                .setParameter("partOfSpeechId", partOfSpeechId)
                                .executeUpdate()
                )
                .replaceWithVoid();
    }

    public Uni<Boolean> existsPartOfSpeechAssignment(Long vocabularyId, Long partOfSpeechId) {
        return Panache.getSession()
                .flatMap(session -> session.createQuery("""
                                select count(assignment) from VocabularyPartOfSpeech assignment
                                where assignment.vocabularyId = :vocabularyId
                                  and assignment.partOfSpeechId = :partOfSpeechId
                                """, Long.class)
                        .setParameter("vocabularyId", vocabularyId)
                        .setParameter("partOfSpeechId", partOfSpeechId)
                        .getSingleResult())
                .map(assignmentCount -> assignmentCount > 0);
    }
}
