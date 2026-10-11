package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.player.dto.QuizGameConfigResponse;
import com.japaneselearning.vocabulary.entity.JlptLevel;
import com.japaneselearning.vocabulary.entity.Lesson;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.HashSet;
import java.util.List;

@ApplicationScoped
public class QuizGameRepository {
    public Uni<JlptLevel> findLevelForShare(Long id) {
        return Panache.getSession().chain(session -> session.find(
                JlptLevel.class, id, LockModeType.PESSIMISTIC_READ
        ));
    }

    public Uni<Lesson> findLessonForShare(Long id) {
        return Panache.getSession().chain(session -> session.find(
                Lesson.class, id, LockModeType.PESSIMISTIC_READ
        ));
    }

    public Uni<List<Long>> selectRandom(Long levelId, Long lessonId, int count) {
        return Panache.getSession().chain(session -> session.createNativeQuery(
                        "SELECT q.id " + QuizQuestionRepository.PLAYABLE_FILTER + " ORDER BY RAND()",
                        Long.class
                )
                .setParameter("levelId", levelId).setParameter("lessonId", lessonId)
                .setMaxResults(count).getResultList());
    }

    public Uni<Boolean> stillMatches(List<Long> ids, Long levelId, Long lessonId) {
        // Locking/current reads avoid an earlier candidate query's repeatable-read snapshot.
        // The caller already owns all question locks, so Admin classification edits are serialized.
        return Panache.getSession().chain(session -> {
            Uni<Boolean> levelMatches = levelId == null
                    ? Uni.createFrom().item(true)
                    : session.createNativeQuery("""
                            SELECT question_id FROM quiz_question_levels
                            WHERE question_id IN (:ids) AND level_id = :levelId FOR SHARE
                            """, Long.class
                    ).setParameter("ids", ids).setParameter("levelId", levelId)
                    .getResultList().chain(explicit -> session.createNativeQuery("""
                                    SELECT ql.question_id FROM quiz_question_lessons ql
                                    JOIN lessons l ON l.id = ql.lesson_id
                                    WHERE ql.question_id IN (:ids) AND l.level_id = :levelId FOR SHARE
                                    """, Long.class
                            ).setParameter("ids", ids).setParameter("levelId", levelId)
                            .getResultList().map(implied -> {
                                var matched = new HashSet<>(explicit);

                                matched.addAll(implied);

                                return matched.containsAll(ids);
                            }));

            return levelMatches.chain(matches -> !matches || lessonId == null
                    ? Uni.createFrom().item(matches)
                    : session.createNativeQuery("""
                            SELECT ql.question_id FROM quiz_question_lessons ql
                            JOIN lessons l ON l.id = ql.lesson_id
                            WHERE ql.question_id IN (:ids) AND ql.lesson_id = :lessonId
                              AND (:levelId IS NULL OR l.level_id = :levelId) FOR SHARE
                            """, Long.class
                    ).setParameter("ids", ids).setParameter("lessonId", lessonId).setParameter("levelId", levelId)
                    .getResultList().map(found -> found.containsAll(ids)));
        });
    }

    public Uni<QuizGameConfigResponse> configuration(int maxCount) {
        return countPlayable().chain(total -> levels().chain(levels -> lessons()
                .map(lessons -> new QuizGameConfigResponse(total, maxCount, levels, lessons))
        ));
    }

    private Uni<Long> countPlayable() {
        return Panache.getSession().chain(session -> session.createNativeQuery(
                        "SELECT COUNT(*) " + QuizQuestionRepository.PLAYABLE_FILTER,
                        Long.class
                )
                .setParameter("levelId", null).setParameter("lessonId", null).getSingleResult());
    }

    private Uni<List<QuizGameConfigResponse.Level>> levels() {
        String query = """
                SELECT l.id, l.code, l.name, COUNT(DISTINCT p.id)
                FROM jlpt_levels l JOIN (SELECT q.id
                """ + QuizQuestionRepository.PLAYABLE_FILTER + """
                ) p ON EXISTS (SELECT 1 FROM quiz_question_levels ql
                               WHERE ql.question_id = p.id AND ql.level_id = l.id)
                    OR EXISTS (SELECT 1 FROM quiz_question_lessons ql JOIN lessons lesson ON lesson.id = ql.lesson_id
                               WHERE ql.question_id = p.id AND lesson.level_id = l.id)
                GROUP BY l.id, l.code, l.name, l.display_order ORDER BY l.display_order, l.id
                """;

        return Panache.getSession().chain(session -> session.createNativeQuery(query, Object[].class)
                        .setParameter("levelId", null).setParameter("lessonId", null).getResultList())
                .map(rows -> rows.stream().map(row -> new QuizGameConfigResponse.Level(
                        number(row[0]), (String) row[1], (String) row[2], number(row[3])
                )).toList());
    }

    private Uni<List<QuizGameConfigResponse.Lesson>> lessons() {
        String query = """
                SELECT l.id, l.level_id, l.lesson_number, l.title, COUNT(DISTINCT p.id)
                FROM lessons l JOIN quiz_question_lessons ql ON ql.lesson_id = l.id
                JOIN (SELECT q.id
                """ + QuizQuestionRepository.PLAYABLE_FILTER + """
                ) p ON p.id = ql.question_id
                GROUP BY l.id, l.level_id, l.lesson_number, l.title, l.display_order
                ORDER BY l.level_id, l.display_order, l.id
                """;

        return Panache.getSession().chain(session -> session.createNativeQuery(query, Object[].class)
                        .setParameter("levelId", null).setParameter("lessonId", null).getResultList())
                .map(rows -> rows.stream().map(row -> new QuizGameConfigResponse.Lesson(
                        number(row[0]), number(row[1]), ((Number) row[2]).intValue(), (String) row[3], number(row[4])
                )).toList());
    }

    private long number(Object value) {
        return ((Number) value).longValue();
    }
}
