package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.domain.QuizSessionStatus;
import com.japaneselearning.quiz.entity.QuizSession;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.panache.common.Page;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;

@ApplicationScoped
public class QuizSessionRepository implements PanacheRepository<QuizSession> {
    public Uni<QuizSession> findOwnedById(Long id, String userSubject) {
        return find("id = ?1 and userSubject = ?2", id, userSubject).firstResult();
    }

    public Uni<QuizSession> findOwnedByIdForUpdate(Long id, String userSubject) {
        return find("id = ?1 and userSubject = ?2", id, userSubject)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
    }

    public Uni<Long> countCompletedBySubject(String subject) {
        return count("userSubject = ?1 and status = ?2", subject, QuizSessionStatus.COMPLETED);
    }

    public Uni<List<QuizSession>> findCompletedBySubject(String subject, int page, int size) {
        return find(
                "userSubject = ?1 and status = ?2 order by completedAt desc, id desc",
                subject,
                QuizSessionStatus.COMPLETED
        )
                .page(Page.of(page, size))
                .list();
    }
}
