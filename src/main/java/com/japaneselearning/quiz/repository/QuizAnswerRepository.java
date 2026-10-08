package com.japaneselearning.quiz.repository;

import com.japaneselearning.quiz.entity.QuizAnswer;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuizAnswerRepository implements PanacheRepository<QuizAnswer> {
}
