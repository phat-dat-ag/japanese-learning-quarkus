package com.japaneselearning.vocabulary.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LessonVocabularyRepository {

    public Uni<Void> insert(
            Long lessonId,
            Long vocabularyId,
            Integer displayOrder) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        INSERT INTO lesson_vocabulary (
                                            lesson_id,
                                            vocabulary_id,
                                            display_order
                                        )
                                        VALUES (
                                            :lessonId,
                                            :vocabularyId,
                                            :displayOrder
                                        )
                                        ON DUPLICATE KEY UPDATE
                                            display_order =
                                                VALUES(display_order)
                                        """)
                                .setParameter(
                                        "lessonId",
                                        lessonId
                                )
                                .setParameter(
                                        "vocabularyId",
                                        vocabularyId
                                )
                                .setParameter(
                                        "displayOrder",
                                        displayOrder
                                )
                                .executeUpdate()
                )
                .replaceWithVoid();
    }

    public Uni<Void> deleteByVocabularyId(
            Long vocabularyId) {

        return Panache.getSession()
                .flatMap(session ->
                        session.createNativeMutationQuery("""
                                        DELETE FROM lesson_vocabulary
                                        WHERE vocabulary_id = :vocabularyId
                                        """)
                                .setParameter(
                                        "vocabularyId",
                                        vocabularyId
                                )
                                .executeUpdate()
                )
                .replaceWithVoid();
    }
}