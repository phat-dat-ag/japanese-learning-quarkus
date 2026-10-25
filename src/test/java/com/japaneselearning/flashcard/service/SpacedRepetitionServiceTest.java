package com.japaneselearning.flashcard.service;

import com.japaneselearning.flashcard.domain.LearningStatus;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.domain.ReviewRating;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;

class SpacedRepetitionServiceTest {
    private final SpacedRepetitionService srs = new SpacedRepetitionService(600, 365, 5, 21);
    private final Instant now = Instant.parse("2026-10-24T00:00:00Z");

    @Test
    void unseenAndFirstRatings() {
        var unseen = ProgressState.unseen();
        assertEquals(LearningStatus.NEW, unseen.status());
        assertNull(unseen.nextReviewAt());
        long[] intervals = {600, 86400, 86400, 345600};
        int index = 0;
        for (ReviewRating rating : ReviewRating.values()) {
            var result = srs.review(unseen, rating, now);
            assertEquals(intervals[index++], result.intervalSeconds());
            assertEquals(now.plusSeconds(result.intervalSeconds()), result.nextReviewAt());
            assertEquals(0, result.lapseCount());
            assertEquals(rating == ReviewRating.AGAIN ? 0 : 1, result.successfulRepetitionCount());
            assertEquals(1, result.version());
        }
        assertEquals(LearningStatus.NEW, unseen.status());
    }

    @Test
    void repeatedGoodReviewsMasterAndStillSchedule() {
        var state = ProgressState.unseen();
        long[] days = {1, 3, 8, 20, 50};
        for (long day : days) {
            state = srs.review(state, ReviewRating.GOOD, now);
            assertEquals(day * 86400, state.intervalSeconds());
        }
        assertEquals(LearningStatus.MASTERED, state.status());
        assertEquals(now, state.masteredAt());
        assertEquals(now.plusSeconds(50 * 86400), state.nextReviewAt());
        var forgotten = srs.review(state, ReviewRating.AGAIN, now.plusSeconds(50 * 86400));
        assertEquals(LearningStatus.LEARNING, forgotten.status());
        assertEquals(0, forgotten.successfulRepetitionCount());
        assertEquals(1, forgotten.lapseCount());
        assertNull(forgotten.masteredAt());
        assertEquals(86400, srs.review(forgotten, ReviewRating.GOOD, forgotten.nextReviewAt()).intervalSeconds());
    }

    @Test
    void hardAndEasyUsePreviousIntervalAndBoundEaseAndSchedule() {
        var state = srs.review(ProgressState.unseen(), ReviewRating.HARD, now);
        state = srs.review(state, ReviewRating.HARD, state.nextReviewAt());
        assertEquals(2 * 86400, state.intervalSeconds());
        assertEquals(LearningStatus.REVIEW, state.status());
        for (int i = 0; i < 100; i++) {
            state = srs.review(state, ReviewRating.EASY, state.nextReviewAt());
        }
        assertEquals(365 * 86400, state.intervalSeconds());
        assertEquals(3.0, state.easeFactor());
        for (int i = 0; i < 100; i++) {
            state = srs.review(state, ReviewRating.AGAIN, state.nextReviewAt());
        }
        assertEquals(1.3, state.easeFactor());
        assertEquals(100, state.lapseCount());
    }

    @Test
    void validatesConfigurationAndInvalidState() {
        assertThrows(IllegalArgumentException.class, () -> new SpacedRepetitionService(0, 365, 5, 21));
        assertThrows(IllegalArgumentException.class, () -> new SpacedRepetitionService(600, 10, 5, 21));
        var invalid = new ProgressState(LearningStatus.REVIEW, 1, 0, 100, Double.NaN, now, now, null, 1);
        assertThrows(IllegalArgumentException.class, () -> srs.review(invalid, ReviewRating.GOOD, now));
    }

    @Test
    void successfulMixedRatingsNeverReduceIntervals() {
        var remembered = new ReviewRating[]{ReviewRating.HARD, ReviewRating.GOOD, ReviewRating.EASY};
        for (ReviewRating first : remembered) {
            for (ReviewRating second : remembered) {
                var previous = srs.review(ProgressState.unseen(), first, now);
                var next = srs.review(previous, second, previous.nextReviewAt());
                assertTrue(next.intervalSeconds() >= previous.intervalSeconds(), first + " then " + second);
                assertEquals(previous.nextReviewAt().plusSeconds(next.intervalSeconds()), next.nextReviewAt());
            }
        }
        var easy = srs.review(ProgressState.unseen(), ReviewRating.EASY, now);
        assertEquals(4 * 86400, srs.review(easy, ReviewRating.GOOD, easy.nextReviewAt()).intervalSeconds());
    }

    @Test
    void reducedConfigurationCapPreservesExistingSuccessfulIntervalButAgainResets() {
        var previous = new ProgressState(LearningStatus.MASTERED, 10, 0, 365 * 86400L,
                2.5, now, now.plusSeconds(365 * 86400L), now, 10);
        var lowerCap = new SpacedRepetitionService(600, 30, 5, 21);
        assertEquals(previous.intervalSeconds(), lowerCap.review(previous, ReviewRating.HARD, previous.nextReviewAt()).intervalSeconds());
        assertEquals(600, lowerCap.review(previous, ReviewRating.AGAIN, previous.nextReviewAt()).intervalSeconds());
    }

    @Test
    void clockUsesUtcMicroseconds() {
        var instant = Instant.parse("2026-10-24T00:00:00.123456789Z");
        var clock = new FlashcardTime(java.time.Clock.fixed(instant, java.time.ZoneId.of("Asia/Bangkok")));
        assertEquals(Instant.parse("2026-10-24T00:00:00.123456Z"), clock.now());
        assertEquals(clock.now(), FlashcardTime.exposed(FlashcardTime.stored(clock.now())));
    }
}
