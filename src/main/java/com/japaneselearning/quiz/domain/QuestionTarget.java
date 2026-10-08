package com.japaneselearning.quiz.domain;

public record QuestionTarget(int start, int length, String reading) {
    public QuestionTarget {
        QuizQuestionRules.validateTarget(start, length, reading);
    }

    public void validateAgainst(String sentence) {
        QuizQuestionRules.validateSentence(sentence);

        int count = sentence.codePointCount(0, sentence.length());

        if (start > count || length > count - start) {
            throw QuizQuestionRules.invalid(
                    "target", "Target exceeds the sentence code-point range"
            );
        }

        int begin = sentence.offsetByCodePoints(0, start);
        int end = sentence.offsetByCodePoints(begin, length);

        if (!sentence.substring(begin, end).equals(reading)) {
            throw QuizQuestionRules.invalid(
                    "targetReading", "Target reading must exactly match the selected segment"
            );
        }
    }
}
