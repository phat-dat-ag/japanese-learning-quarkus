package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizQuestionLesson;
import com.japaneselearning.quiz.entity.id.QuizQuestionLessonId;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuizQuestionLessonRepository
        implements PanacheRepositoryBase<QuizQuestionLesson, QuizQuestionLessonId> {
}
