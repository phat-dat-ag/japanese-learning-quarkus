package com.japaneselearning.quiz.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import org.hibernate.annotations.Immutable;

@Entity
@Table(name = "quiz_session_options")
@Immutable
public class QuizSessionOption {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    public Long id;

    @Column(name = "session_question_id", nullable = false)
    public Long sessionQuestionId;

    @Column(name = "option_text", nullable = false, length = 200)
    public String optionText;

    @Column(name = "is_correct", nullable = false)
    public boolean correct;
}
