package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizAnswer;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

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
}
