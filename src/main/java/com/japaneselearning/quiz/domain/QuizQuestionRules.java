package com.japaneselearning.quiz.domain;

import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.entity.QuizQuestion;

import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;

public final class QuizQuestionRules {
    private QuizQuestionRules() {
    }

    public static void validatePublication(
            QuizQuestion question,
            String sentence,
            List<QuestionOption> options
    ) {
        if (question.sourceType == null || question.status == null) {
            throw invalid("source", "Question source and status are required");
        }

        if (question.sourceType == QuestionSource.EXAMPLE) {
            if (
                    question.exampleSentenceId == null || question.sentenceReading != null
            ) {
                throw invalid(
                        "source",
                        "EXAMPLE questions reference an example and cannot own a sentence"
                );
            }
        } else if (
                question.exampleSentenceId != null || !java.util.Objects.equals(sentence, question.sentenceReading)
        ) {
            throw invalid(
                    "source",
                    "CUSTOM questions own their sentence and cannot reference an example"
            );
        }

        new QuestionTarget(
                question.targetStart,
                question.targetLength,
                question.targetReading
        ).validateAgainst(sentence);

        validateOptions(options);
        optionalText(question.explanationVi, "explanationVi", 2000);
        optionalText(question.explanationEn, "explanationEn", 2000);
    }

    public static void validateOptions(List<QuestionOption> options) {
        if (options == null || options.size() != 4) {
            throw invalid("options", "Publication requires exactly four options");
        }

        var distinct = new HashSet<String>();
        int correct = 0;

        for (QuestionOption option : options) {
            if (option == null) {
                throw invalid("options", "Options must not be null");
            }

            requiredText(option.text(), "options", 200);

            if (!option.text().equals(option.text().strip())
                    || !distinct.add(Normalizer.normalize(option.text(), Normalizer.Form.NFKC))
            ) {
                throw invalid(
                        "options",
                        "Options must be distinct after NFKC normalization and have no surrounding whitespace"
                );
            }

            if (option.correct()) {
                correct++;
            }
        }

        if (correct != 1) {
            throw invalid("options", "Publication requires exactly one correct option");
        }
    }

    static void validateTarget(int start, int length, String reading) {
        requiredText(reading, "targetReading", 200);

        if (start < 0 || length <= 0 || reading.codePointCount(0, reading.length()) != length) {
            throw invalid(
                    "target",
                    "Target uses a zero-based code-point start and a positive matching length"
            );
        }

        if (
                !isKanaLetter(reading.codePointAt(0)) || !reading.codePoints().allMatch(cp -> isKanaLetter(cp)
                        || cp == 0x3099 || cp == 0x309A || cp == 0x30FC || cp == 0xFF70
                        || cp == 0xFF9E || cp == 0xFF9F
                )
        ) {
            throw invalid("targetReading", "Target reading must contain Hiragana or Katakana");
        }
    }

    private static boolean isKanaLetter(int cp) {
        var script = Character.UnicodeScript.of(cp);

        return Character.isLetter(cp) && (
                script == Character.UnicodeScript.HIRAGANA
                        || script == Character.UnicodeScript.KATAKANA
        );
    }

    static void validateSentence(String sentence) {
        requiredText(sentence, "sentenceReading", 1000);
    }

    private static void requiredText(String text, String field, int maximum) {
        if (text == null || text.isBlank()) {
            throw invalid(field, "Value is required");
        }

        optionalText(text, field, maximum);
    }

    private static void optionalText(String text, String field, int maximum) {
        if (text != null && (
                text.codePointCount(0, text.length()) > maximum
                        || text.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF)
        )
        ) {
            throw invalid(
                    field,
                    "Value exceeds the code-point limit or contains invalid Unicode"
            );
        }
    }

    public static ValidationException invalid(String field, String message) {
        return new ValidationException("QUIZ_QUESTION_INVALID", "Invalid quiz question",
                List.of(new ValidationError(field, message)));
    }
}
