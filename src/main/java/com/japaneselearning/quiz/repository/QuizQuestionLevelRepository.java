package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizQuestionLevel;
import com.japaneselearning.quiz.entity.id.QuizQuestionLevelId;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuizQuestionLevelRepository
        implements PanacheRepositoryBase<QuizQuestionLevel, QuizQuestionLevelId> {
}
