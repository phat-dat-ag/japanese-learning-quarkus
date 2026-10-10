package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.domain.ExampleReading;
import com.japaneselearning.quiz.entity.QuizQuestion;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;

@ApplicationScoped
public class QuizQuestionRepository implements PanacheRepository<QuizQuestion> {
    static final String PLAYABLE_FILTER = """
            FROM quiz_questions q
            LEFT JOIN example_sentences e ON e.id = q.example_sentence_id
            WHERE q.status = 'PUBLISHED'
              AND (q.source_type = 'CUSTOM' OR (e.id IS NOT NULL AND
                q.validated_example_fingerprint =
                    SHA2(CONCAT(e.japanese_reading, CHAR(0), CAST(e.updated_at AS CHAR)), 256)))
              AND (:levelId IS NULL OR EXISTS (
                SELECT 1 FROM quiz_question_levels ql WHERE ql.question_id = q.id AND ql.level_id = :levelId)
                OR EXISTS (SELECT 1 FROM quiz_question_lessons ql JOIN lessons l ON l.id = ql.lesson_id
                    WHERE ql.question_id = q.id AND l.level_id = :levelId))
              AND (:lessonId IS NULL OR EXISTS (
                SELECT 1 FROM quiz_question_lessons ql JOIN lessons l ON l.id = ql.lesson_id
                WHERE ql.question_id = q.id AND ql.lesson_id = :lessonId
                  AND (:levelId IS NULL OR l.level_id = :levelId)))
            """;

    public Uni<QuizQuestion> findByIdForUpdate(Long id) {
        return find("id", id).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }

    public Uni<ExampleReading> findExampleReadingForUpdate(Long exampleId) {
        return Panache.getSession().chain(session -> session.createNativeQuery("""
                                SELECT japanese_reading,
                                       SHA2(CONCAT(japanese_reading, CHAR(0), CAST(updated_at AS CHAR)), 256)
                                FROM example_sentences WHERE id = :id FOR UPDATE
                                """, Object[].class)
                        .setParameter("id", exampleId).getSingleResultOrNull())
                .map(row -> row == null ? null : new ExampleReading((String) row[0], (String) row[1]));
    }

    public Uni<Boolean> hasExampleVocabularyLink(Long vocabularyId, Long exampleId) {
        return Panache.getSession().chain(session -> session.createQuery("""
                        select count(link) from VocabularyExample link
                        where link.vocabularyId = :vocabularyId and link.exampleSentenceId = :exampleId
                        """, Long.class).setParameter("vocabularyId", vocabularyId)
                .setParameter("exampleId", exampleId).getSingleResult()).map(count -> count > 0);
    }

    // Candidate selection is not authorization to start a game: recheck and snapshot
    // selected questions under the same locks used for publication.
    public Uni<List<Long>> findEligibleIds(Long levelId, Long lessonId, long afterId, int limit) {
        if (afterId < 0 || limit < 1 || limit > 100) {
            throw new IllegalArgumentException("Quiz candidate page requires afterId >= 0 and limit 1..100");
        }
        return Panache.getSession().chain(session -> session.createNativeQuery(
                        "SELECT q.id " + PLAYABLE_FILTER + " AND q.id > :afterId ORDER BY q.id",
                        Long.class
                )
                .setParameter("afterId", afterId).setParameter("levelId", levelId)
                .setParameter("lessonId", lessonId).setMaxResults(limit).getResultList());
    }
}
