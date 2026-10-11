package com.japaneselearning.quiz.domain;

import com.japaneselearning.quiz.entity.QuizQuestion;

import java.util.List;
import java.util.Objects;

public record QuestionSnapshot(
        Long questionId,
        long questionVersion,
        QuestionSource source,
        Long sourceExampleId,
        String sentenceReading,
        QuestionTarget target,
        String explanationVi,
        String explanationEn,
        List<QuestionOption> options
) {
    public QuestionSnapshot {
        if (questionId == null
                || questionVersion < 0
                || source == null
                || target == null
                || (source == QuestionSource.EXAMPLE && sourceExampleId == null)
                || (source == QuestionSource.CUSTOM && sourceExampleId != null)
        ) {
            throw QuizQuestionRules.invalid(
                    "snapshot", "Snapshot requires a persisted valid question"
            );
        }

        target.validateAgainst(sentenceReading);
        QuizQuestionRules.validateOptions(options);
        options = List.copyOf(options);
    }

    public static QuestionSnapshot capture(
            QuizQuestion question,
            ExampleReading example,
            List<QuestionOption> options
    ) {
        if (question.status != QuestionStatus.PUBLISHED) {
            throw QuizQuestionRules.invalid(
                    "status", "Only published questions can enter a game"
            );
        }

        String sentence = question.sentenceReading;

        if (question.sourceType == QuestionSource.EXAMPLE) {
            if (example == null
                    || question.validatedExampleFingerprint == null
                    || !Objects.equals(question.validatedExampleFingerprint, example.fingerprint())
            ) {
                throw QuizQuestionRules.invalid(
                        "example", "Example changed or was removed; revalidation is required"
                );
            }

            sentence = example.reading();
        }

        QuizQuestionRules.validatePublication(question, sentence, options);

        return new QuestionSnapshot(
                question.id,
                question.version,
                question.sourceType,
                question.exampleSentenceId,
                sentence,
                new QuestionTarget(question.targetStart, question.targetLength, question.targetReading),
                question.explanationVi,
                question.explanationEn,
                options
        );
    }
}
