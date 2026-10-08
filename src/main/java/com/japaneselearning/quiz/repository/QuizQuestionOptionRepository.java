package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizQuestionOption;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;

@ApplicationScoped
public class QuizQuestionOptionRepository implements PanacheRepository<QuizQuestionOption> {
    public Uni<List<QuizQuestionOption>> findByQuestionId(Long questionId) {
        return find("questionId", questionId)
                .withLock(LockModeType.PESSIMISTIC_READ)
                .list();
    }
}
