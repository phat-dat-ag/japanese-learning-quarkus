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
}