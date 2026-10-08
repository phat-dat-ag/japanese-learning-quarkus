package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizSession;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class QuizSessionRepository implements PanacheRepository<QuizSession> {
    public Uni<QuizSession> findOwnedByIdForUpdate(Long id, String userSubject) {
        return find("id = ?1 and userSubject = ?2", id, userSubject)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }
}
