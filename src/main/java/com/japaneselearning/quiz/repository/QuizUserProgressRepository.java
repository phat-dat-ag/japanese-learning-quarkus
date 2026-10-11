package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizUserProgress;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class QuizUserProgressRepository implements PanacheRepository<QuizUserProgress> {
    public Uni<QuizUserProgress> findForUpdate(String userSubject, Long questionId) {
        return find("userSubject = ?1 and questionId = ?2", userSubject, questionId)
                .withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }
}
