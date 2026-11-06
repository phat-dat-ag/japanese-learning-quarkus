package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.Lesson;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;

@ApplicationScoped
public class LessonRepository implements PanacheRepository<Lesson> {

    public Uni<Boolean> hasDeletionReferences(Long lessonId) {
        // Parent FOR UPDATE prevents new FK assignments while these checks and deletion run.
        // Historical snapshots deliberately have no FK, but still prevent catalogue deletion.
        return Panache.getSession().flatMap(session -> session.createNativeQuery("""
                                        SELECT EXISTS(SELECT 1 FROM lesson_vocabulary WHERE lesson_id = :id)
                                            OR EXISTS(SELECT 1 FROM quiz_question_lessons WHERE lesson_id = :id)
                                            OR EXISTS(SELECT 1 FROM flashcard_sessions WHERE lesson_id = :id)
                                            OR EXISTS(SELECT 1 FROM quiz_session_question_lessons WHERE lesson_id = :id)
                                        """,
                                Integer.class
                        )
                        .setParameter("id", lessonId)
                        .getSingleResult()
        ).map(result -> result != 0);
    }

    public Uni<Long> deleteIfUnreferenced(Long lessonId) {
        // Keep the guard in the DELETE too: existing CASCADE rules must never erase assignments.
        return Panache.getSession().flatMap(session -> session.createNativeMutationQuery("""
                        DELETE FROM lessons WHERE id = :id
                          AND NOT EXISTS(SELECT 1 FROM lesson_vocabulary WHERE lesson_id = :id)
                          AND NOT EXISTS(SELECT 1 FROM quiz_question_lessons WHERE lesson_id = :id)
                          AND NOT EXISTS(SELECT 1 FROM flashcard_sessions WHERE lesson_id = :id)
                          AND NOT EXISTS(SELECT 1 FROM quiz_session_question_lessons WHERE lesson_id = :id)
                        """)
                .setParameter("id", lessonId)
                .executeUpdate()
        ).map(Integer::longValue);
    }

    public Uni<Lesson> findByLevelIdAndLessonNumber(Long levelId, Integer lessonNumber) {
        return find(
                "levelId = ?1 and lessonNumber = ?2",
                levelId,
                lessonNumber
        ).firstResult();
    }

    public Uni<Lesson> findByLevelIdAndDisplayOrder(Long levelId, Integer displayOrder) {
        return find(
                "levelId = ?1 and displayOrder = ?2",
                levelId, displayOrder
        ).firstResult();
    }

    public Uni<Lesson> findByIdForUpdate(Long lessonId) {
        return find("id", lessonId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
    }

    public Uni<List<Lesson>> findByLevelId(Long levelId) {
        return find(
                "levelId = ?1 order by displayOrder",
                levelId
        ).list();
    }
}
