package com.japaneselearning.quiz.domain;

import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.entity.QuizQuestion;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class QuizQuestionRulesTest {
    private static final String READING = "\u304c\u3063\u3053\u3046";
    private static final String SENTENCE = READING + "\u3078\u3044\u304f\u3002";

    private static List<QuestionOption> options() {
        return List.of(
                new QuestionOption("\u5b66\u6821", true),
                new QuestionOption("\u5b66\u751f", false),
                new QuestionOption("\u5148\u751f", false),
                new QuestionOption("\u4f1a\u793e", false)
        );
    }

    private static QuizQuestion custom() {
        QuizQuestion question = new QuizQuestion();
        question.id = 1L;
        question.sourceType = QuestionSource.CUSTOM;
        question.status = QuestionStatus.DRAFT;
        question.sentenceReading = SENTENCE;
        question.targetLength = 4;
        question.targetReading = READING;

        return question;
    }

    @Test
    void indexesCodePointsIncludingSupplementaryCharacters() {
        new QuestionTarget(1, 4, READING).validateAgainst("\uD83D\uDE00" + SENTENCE);

        assertThrows(ValidationException.class, () -> new QuestionTarget(2, 4, READING).validateAgainst("\uD83D\uDE00" + SENTENCE));

        new QuestionTarget(0, 3, "\uD82C\uDC00\u30ab\u30ca").validateAgainst("\uD82C\uDC00\u30ab\u30ca");
    }

    @Test
    void acceptsKanaWithoutNormalizingSourceOffsets() {
        new QuestionTarget(0, 2, "\uff76\uff9e").validateAgainst("\uff76\uff9e");
        new QuestionTarget(0, 3, "\u30b3\u30fc\u30d2").validateAgainst("\u30b3\u30fc\u30d2\u30fc");
        new QuestionTarget(0, 2, "\u304b\u3099").validateAgainst("\u304b\u3099\u3063\u3053\u3046");

        assertThrows(ValidationException.class, () -> new QuestionTarget(0, 1, "\u304c").validateAgainst("\u304b\u3099"));
        assertThrows(ValidationException.class, () -> new QuestionTarget(0, 2, "\u5b66\u6821"));
        assertThrows(ValidationException.class, () -> new QuestionTarget(0, 1, "\u30fc"));
    }

    @Test
    void rejectsInvalidRangesAndUnpairedSurrogates() {
        assertThrows(ValidationException.class, () -> new QuestionTarget(-1, 4, READING));
        assertThrows(ValidationException.class, () -> new QuestionTarget(0, 0, READING));
        assertThrows(ValidationException.class, () -> new QuestionTarget(Integer.MAX_VALUE, 4, READING).validateAgainst(SENTENCE));
        assertThrows(ValidationException.class, () -> new QuestionTarget(0, 4, READING).validateAgainst(READING + "\uD800"));
        assertThrows(ValidationException.class, () -> new QuestionTarget(0, 4, READING).validateAgainst(""));
    }

    @Test
    void publicationRequiresFourDistinctOptionsAndOneCorrect() {
        QuizQuestionRules.validatePublication(custom(), SENTENCE, options());
        assertThrows(ValidationException.class, () -> QuizQuestionRules.validateOptions(options().subList(0, 3)));
        assertThrows(ValidationException.class, () ->
                QuizQuestionRules.validateOptions(options().stream().map(o ->
                        new QuestionOption(o.text(), false)).toList()
                )
        );
        assertThrows(ValidationException.class, () ->
                QuizQuestionRules.validateOptions(options().stream().map(o ->
                        new QuestionOption(o.text(), true)).toList()
                )
        );
        assertThrows(ValidationException.class, () -> QuizQuestionRules.validateOptions(List.of(
                new QuestionOption("\u30ac", true), new QuestionOption("\uff76\uff9e", false), options().get(2), options().get(3))));
        assertThrows(ValidationException.class, () -> QuizQuestionRules.validateOptions(List.of(
                new QuestionOption("padded ", true), options().get(1), options().get(2), options().get(3))));
    }

    @Test
    void enforcesSourceOwnershipAndRevalidation() {
        QuizQuestion question = custom();
        question.sourceType = QuestionSource.EXAMPLE;
        question.exampleSentenceId = 7L;
        assertThrows(ValidationException.class, () -> QuizQuestionRules.validatePublication(question, READING, options()));
        question.sentenceReading = null;
        question.status = QuestionStatus.PUBLISHED;
        question.validatedExampleFingerprint = "validated";
        assertThrows(ValidationException.class, () -> QuestionSnapshot.capture(question, null, options()));
        assertThrows(ValidationException.class, () -> QuestionSnapshot.capture(question, new ExampleReading(READING, "changed"), options()));
        assertEquals(READING, QuestionSnapshot.capture(question, new ExampleReading(READING, "validated"), options()).sentenceReading());
    }

    @Test
    void snapshotsRemainIndependentAcrossBankEdits() {
        QuizQuestion question = custom();
        question.status = QuestionStatus.PUBLISHED;

        var choices = new ArrayList<>(options());
        var snapshot = QuestionSnapshot.capture(question, null, choices);

        choices.clear();
        question.sentenceReading = "changed";
        question.status = QuestionStatus.DRAFT;
        assertEquals(SENTENCE, snapshot.sentenceReading());
        assertEquals(4, snapshot.options().size());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.options().clear());
        assertThrows(ValidationException.class, () -> QuestionSnapshot.capture(question, null, options()));
    }
}
