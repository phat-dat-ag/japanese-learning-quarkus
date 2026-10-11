package com.japaneselearning.quiz.repository;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuizClassificationSnapshotRepository {
    private static final String CAPTURE_LESSONS =
            """
                            INSERT INTO quiz_session_question_lessons (session_question_id, lesson_id, level_id)
                            SELECT q.id, c.lesson_id, l.level_id
                            FROM quiz_session_questions q
                            JOIN quiz_question_lessons c ON c.question_id = q.question_id
                            JOIN lessons l ON l.id = c.lesson_id
                            WHERE q.session_id = :sessionId
                    """;
    private static final String CAPTURE_LEVELS =
            """
                            INSERT INTO quiz_session_question_levels (session_question_id, level_id)
                            SELECT q.id, c.level_id
                            FROM quiz_session_questions q
                            JOIN quiz_question_levels c ON c.question_id = q.question_id
                            WHERE q.session_id = :sessionId
                            UNION
                            SELECT q.id, l.level_id
                            FROM quiz_session_questions q
                            JOIN quiz_session_question_lessons l ON l.session_question_id = q.id
                            WHERE q.session_id = :sessionId
                    """;
    private static final String MARK_CAPTURED =
            """
                            UPDATE quiz_session_questions SET classifications_captured = TRUE
                            WHERE session_id = :sessionId
                    """;

    // Creation owns the transaction and bank question locks; snapshots must be flushed first.
    // INSERT SELECT uses current locking reads rather than the earlier candidate read view.
    public Uni<Void> capture(Long sessionId) {
        return Panache.getSession()
                .chain(session -> {
                    if (session.currentTransaction() == null) {
                        return Uni.createFrom().failure(new IllegalStateException(
                                "Classification capture requires a transaction"
                        ));
                    }

                    return session.createNativeMutationQuery(CAPTURE_LESSONS)
                            .setParameter("sessionId", sessionId)
                            .executeUpdate()
                            .chain(() -> session.createNativeMutationQuery(CAPTURE_LEVELS)
                                    .setParameter("sessionId", sessionId)
                                    .executeUpdate())
                            .chain(() -> session.createNativeMutationQuery(MARK_CAPTURED)
                                    .setParameter("sessionId", sessionId)
                                    .executeUpdate())
                            .replaceWithVoid();
                });
    }
}
