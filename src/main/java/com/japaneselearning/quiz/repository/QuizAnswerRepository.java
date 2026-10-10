package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizAnswer;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@ApplicationScoped
public class QuizAnswerRepository implements PanacheRepository<QuizAnswer> {
    public record AnswerCounts(long answeredCount, long correctCount) {
    }

    public Uni<AnswerCounts> countBySession(Long sessionId) {
        return Panache.getSession()
                .chain(session -> session.createQuery(
                                """
                                        select count(a), coalesce(sum(case when o.correct = true then 1 else 0 end), 0)
                                        from QuizAnswer a
                                        join QuizSessionQuestion q on q.id = a.sessionQuestionId
                                        join QuizSessionOption o on o.id = a.selectedOptionId and o.sessionQuestionId = q.id
                                        where q.sessionId = :sessionId
                                        """,
                                Object[].class
                        )
                        .setParameter("sessionId", sessionId)
                        .getSingleResult())
                .map(row -> new AnswerCounts(
                        ((Number) row[0]).longValue(),
                        ((Number) row[1]).longValue())
                );
    }

    public Uni<Map<Long, AnswerCounts>> countBySessions(List<Long> sessionIds) {
        if (sessionIds.isEmpty()) {
            return Uni.createFrom().item(Map.of());
        }

        return Panache.getSession()
                .chain(session -> session.createQuery(
                                """
                                        select q.sessionId, count(a),
                                            coalesce(sum(case when o.correct = true
                                                then 1 else 0 end), 0)
                                        from QuizAnswer a
                                        join QuizSessionQuestion q
                                            on q.id = a.sessionQuestionId
                                        join QuizSessionOption o
                                            on o.id = a.selectedOptionId
                                            and o.sessionQuestionId = q.id
                                        where q.sessionId in :ids
                                        group by q.sessionId
                                        """,
                                Object[].class
                        )
                        .setParameter("ids", sessionIds)
                        .getResultList())
                .map(rows -> {
                    Map<Long, AnswerCounts> counts = new HashMap<>();

                    for (Object[] row : rows) {
                        counts.put(
                                ((Number) row[0]).longValue(),
                                new AnswerCounts(
                                        ((Number) row[1]).longValue(),
                                        ((Number) row[2]).longValue()
                                )
                        );
                    }

                    return counts;
                });
    }

    public Uni<List<QuizAnswer>> findBySessionId(Long sessionId) {
        return list(
                "select a from QuizAnswer a join QuizSessionQuestion q on q.id ="
                        + " a.sessionQuestionId where q.sessionId = ?1",
                sessionId
        );
    }
}
