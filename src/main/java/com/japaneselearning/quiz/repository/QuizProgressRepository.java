package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.player.dto.QuizProgressBreakdownResponse.Classification;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class QuizProgressRepository {
    private static final String OVERALL_QUERY =
            """
                    SELECT COUNT(*), COALESCE(SUM(t.answered), 0),
                        COALESCE(SUM(t.correct), 0), COALESCE(MAX(t.correct), 0),
                        MAX(t.completed_at)
                    FROM (
                        SELECT s.id, s.completed_at, COUNT(a.session_question_id) AS answered,
                            SUM(CASE WHEN o.is_correct = 1 THEN 1 ELSE 0 END) AS correct
                        FROM quiz_sessions s
                        LEFT JOIN quiz_session_questions q ON q.session_id = s.id
                        LEFT JOIN quiz_answers a ON a.session_question_id = q.id
                        LEFT JOIN quiz_session_options o ON o.id = a.selected_option_id
                            AND o.session_question_id = q.id
                        WHERE s.user_subject = :subject AND s.status = 'COMPLETED'
                        GROUP BY s.id, s.completed_at
                    ) t
                    """;

    public record BreakdownCounts(
            Classification classification,
            Long levelId,
            Long lessonId,
            long completedSessions,
            long answeredCount,
            long correctCount
    ) {
    }

    public Uni<List<BreakdownCounts>> levels(String subject) {
        return breakdown(subject, false);
    }

    public Uni<List<BreakdownCounts>> lessons(String subject) {
        return breakdown(subject, true);
    }

    private Uni<List<BreakdownCounts>> breakdown(String subject, boolean lessons) {
        String table = lessons ? "quiz_session_question_lessons" : "quiz_session_question_levels";
        String classificationId = lessons ? "c.lesson_id" : "c.level_id";
        String lessonId = lessons ? "c.lesson_id" : "NULL";
        String query =
                """
                        SELECT CASE WHEN q.classifications_captured = FALSE THEN 'UNKNOWN'
                                    WHEN %s IS NULL THEN 'UNASSIGNED' ELSE 'ASSIGNED' END AS classification,
                            c.level_id, %s AS lesson_id, COUNT(DISTINCT s.id), COUNT(a.session_question_id),
                            COALESCE(SUM(CASE WHEN o.is_correct = 1 THEN 1 ELSE 0 END), 0)
                        FROM quiz_sessions s
                        JOIN quiz_session_questions q ON q.session_id = s.id
                        LEFT JOIN %s c ON c.session_question_id = q.id AND q.classifications_captured = TRUE
                        LEFT JOIN quiz_answers a ON a.session_question_id = q.id
                        LEFT JOIN quiz_session_options o ON o.id = a.selected_option_id
                            AND o.session_question_id = q.id
                        WHERE s.user_subject = :subject AND s.status = 'COMPLETED'
                        GROUP BY classification, c.level_id, %s
                        ORDER BY classification, c.level_id, %s
                        """
                        .formatted(classificationId, lessonId, table, lessonId, lessonId);

        return Panache.getSession()
                .chain(session -> session.createNativeQuery(query, Object[].class)
                        .setParameter("subject", subject)
                        .getResultList()
                )
                .map(rows -> rows.stream()
                        .map(row -> new BreakdownCounts(
                                Classification.valueOf(
                                        (String) row[0]),
                                row[1] == null
                                        ? null
                                        : ((Number) row[1])
                                        .longValue(),
                                row[2] == null
                                        ? null
                                        : ((Number) row[2])
                                        .longValue(),
                                ((Number) row[3]).longValue(),
                                ((Number) row[4]).longValue(),
                                ((Number) row[5]).longValue()
                        ))
                        .toList());
    }

    public record OverallCounts(
            long completedSessions,
            long answeredCount,
            long correctCount,
            long bestScore,
            LocalDateTime latestCompletedAt
    ) {
    }

    public Uni<OverallCounts> overall(String subject) {
        return Panache.getSession()
                .chain(session -> session.createNativeQuery(OVERALL_QUERY, Object[].class)
                        .setParameter("subject", subject)
                        .getSingleResult()
                )
                .map(row -> new OverallCounts(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue(),
                        ((Number) row[2]).longValue(),
                        ((Number) row[3]).longValue(),
                        (LocalDateTime) row[4]
                ));
    }
}
