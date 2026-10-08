package com.japaneselearning.quiz.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.quiz.domain.QuestionSource;
import com.japaneselearning.quiz.domain.QuestionStatus;
import com.japaneselearning.quiz.domain.ExampleReading;
import com.japaneselearning.quiz.domain.QuestionOption;
import com.japaneselearning.quiz.domain.QuizQuestionRules;
import com.japaneselearning.quiz.entity.QuizQuestion;
import com.japaneselearning.quiz.repository.QuizQuestionOptionRepository;
import com.japaneselearning.quiz.repository.QuizQuestionRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class QuizPublicationService {
    private final QuizQuestionRepository questions;
    private final QuizQuestionOptionRepository options;

    public QuizPublicationService(
            QuizQuestionRepository questions,
            QuizQuestionOptionRepository options
    ) {
        this.questions = questions;
        this.options = options;
    }

    @WithTransaction
    public Uni<QuizQuestion> prepareForEdit(Long questionId) {
        return requireLocked(questionId).invoke(question -> {
            question.status = QuestionStatus.DRAFT;
            question.validatedExampleFingerprint = null;
        }).call(questions::flush);
    }

    @WithTransaction
    public Uni<QuizQuestion> publish(Long questionId) {
        return requireLocked(questionId).chain(question -> {
            if (question.status != QuestionStatus.DRAFT) {
                return Uni.createFrom().failure(
                        QuizQuestionRules.invalid("status", "Only a draft can be published")
                );
            }

            return resolveSentence(question).chain(sentence ->
                    options.findByQuestionId(question.id).invoke(values -> {
                        QuizQuestionRules.validatePublication(
                                question,
                                sentence.reading(),
                                values.stream().map(
                                        option -> new QuestionOption(option.optionText, option.correct)
                                ).toList()
                        );

                        question.validatedExampleFingerprint = sentence.fingerprint();
                        question.status = QuestionStatus.PUBLISHED;
                    })
            ).call(questions::flush).replaceWith(question);
        });
    }

    private Uni<ExampleReading> resolveSentence(QuizQuestion question) {
        if (question.sourceType == QuestionSource.CUSTOM) {
            return Uni.createFrom().item(
                    new ExampleReading(question.sentenceReading, null)
            );
        }

        if (question.sourceType != QuestionSource.EXAMPLE || question.exampleSentenceId == null) {
            return Uni.createFrom().failure(
                    QuizQuestionRules.invalid("source", "An existing example is required")
            );
        }

        return questions.findExampleReadingForUpdate(question.exampleSentenceId)
                .onItem()
                .ifNull().failWith(() -> QuizQuestionRules.invalid("example", "Example no longer exists"))
                .call(() -> question.vocabularyId == null
                                ? Uni.createFrom().voidItem()
                                : questions.hasExampleVocabularyLink(
                                question.vocabularyId, question.exampleSentenceId
                        ).invoke(linked -> {
                            if (!linked) {
                                throw QuizQuestionRules.invalid(
                                        "vocabularyId", "Vocabulary must reference the selected example"
                                );
                            }
                        }).replaceWithVoid()
                );
    }

    private Uni<QuizQuestion> requireLocked(Long id) {
        return questions.findByIdForUpdate(id).onItem().ifNull().failWith(() ->
                new ResourceNotFoundException("QUIZ_QUESTION_NOT_FOUND", "Quiz question not found"));
    }
}
