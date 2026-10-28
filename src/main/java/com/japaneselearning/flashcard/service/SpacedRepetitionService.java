package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.flashcard.domain.LearningStatus;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.domain.ReviewRating;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

@ApplicationScoped
public class SpacedRepetitionService {
    private static final Logger LOG = Logger.getLogger(SpacedRepetitionService.class);

    public static final String ALGORITHM_VERSION = "simplified-srs-v2";
    private static final long DAY_SECONDS = 86400;

    private final int againSeconds;
    private final int maxDays;
    private final int masteryRepetitions;
    private final int masteryDays;

    public SpacedRepetitionService(
            @ConfigProperty(name = "flashcard.srs.again-seconds", defaultValue = "600")
            int againSeconds,

            @ConfigProperty(name = "flashcard.srs.max-days", defaultValue = "365")
            int maxDays,

            @ConfigProperty(name = "flashcard.srs.mastery-repetitions", defaultValue = "5")
            int masteryRepetitions,

            @ConfigProperty(name = "flashcard.srs.mastery-days", defaultValue = "21")
            int masteryDays
    ) {
        if (againSeconds < 1
                || againSeconds >= DAY_SECONDS
                || maxDays < 4
                || maxDays > 36500
                || masteryRepetitions < 2
                || masteryDays < 1
                || masteryDays > maxDays
        ) {
            throw new IllegalArgumentException("Invalid flashcard SRS configuration");
        }

        this.againSeconds = againSeconds;
        this.maxDays = maxDays;
        this.masteryRepetitions = masteryRepetitions;
        this.masteryDays = masteryDays;
    }

    public ProgressState review(ProgressState previous, ReviewRating rating, Instant now) {
        Objects.requireNonNull(previous);
        Objects.requireNonNull(rating);
        Objects.requireNonNull(now);

        if (previous.successfulRepetitionCount() < 0
                || previous.lapseCount() < 0
                || previous.intervalSeconds() < 0
                || !Double.isFinite(previous.easeFactor())
                || previous.easeFactor() < 1.3
                || previous.easeFactor() > 3.0
        ) {
            throw new IllegalArgumentException("Invalid previous SRS state");
        }

        long repetitions = rating == ReviewRating.AGAIN
                ? 0
                : Math.addExact(previous.successfulRepetitionCount(), 1);

        long lapses = rating == ReviewRating.AGAIN && previous.lastReviewedAt() != null
                ? Math.addExact(previous.lapseCount(), 1)
                : previous.lapseCount();

        double ease = Math.max(1.3, Math.min(3.0, previous.easeFactor() + switch (rating) {
            case AGAIN -> -0.20;
            case HARD -> -0.15;
            case GOOD -> 0;
            case EASY -> 0.15;
        }));

        double previousDays = (double) previous.intervalSeconds() / DAY_SECONDS;

        long interval = againSeconds;

        if (rating != ReviewRating.AGAIN) {
            double days = switch (rating) {
                case HARD -> repetitions == 1 ? 1 : Math.max(previousDays + 1, previousDays * 1.2);
                case GOOD -> repetitions == 1 ? 1 : repetitions == 2 ? 3 : previousDays * ease;
                case EASY -> Math.max(4, previousDays * ease * 1.3);

                default -> throw new IllegalStateException("Unexpected rating");
            };

            interval = (long) Math.min(maxDays, Math.max(1, Math.ceil(days))) * DAY_SECONDS;

            // Successful recall never shortens an existing interval, even after a cap reduction.
            interval = Math.max(previous.intervalSeconds(), interval);
        }

        LearningStatus status = LearningStatus.LEARNING;
        if (rating != ReviewRating.AGAIN) {
            status = repetitions >= 2
                    ? LearningStatus.REVIEW
                    : LearningStatus.LEARNING;

            if (previous.status() == LearningStatus.MASTERED
                    || repetitions >= masteryRepetitions
                    && interval >= (long) masteryDays * DAY_SECONDS
            ) {
                status = LearningStatus.MASTERED;
            }
        }

        Instant masteredAt = status == LearningStatus.MASTERED
                ? previous.masteredAt() == null ? now : previous.masteredAt()
                : null;

        ProgressState result = new ProgressState(
                status,
                repetitions,
                lapses,
                interval,
                ease,
                now,
                now.plusSeconds(interval),
                masteredAt,
                Math.addExact(previous.version(), 1)
        );

        if (LOG.isDebugEnabled()) {
            StructuredLog.event(
                    LOG,
                    Logger.Level.DEBUG,
                    "flashcard.srs.scheduled",
                    "Calculated the next SRS schedule for rating=" + rating + ": "
                            + previous.status() + " -> " + status
                            + ", interval seconds=" + interval
                            + ", repetitions=" + repetitions
                            + ", lapses=" + lapses
                            + ", ease factor=" + ease
                            + ", next review at=" + result.nextReviewAt()
                            + "; progress has not yet been committed",
                    Map.of(
                            "operation", "flashcard.review",
                            "reason", rating.name(),
                            "learning.status", status.name(),
                            "learning.previous_status", previous.status().name(),
                            "lapses", lapses,
                            "intervalSeconds", interval,
                            "repetitions", repetitions
                    )
            );
        }

        return result;
    }
}
