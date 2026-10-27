package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.flashcard.domain.FlashcardSessionStatus;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.dto.FlashcardReviewRequest;
import com.japaneselearning.flashcard.dto.FlashcardReviewResponse;
import com.japaneselearning.flashcard.entity.FlashcardReview;
import com.japaneselearning.flashcard.entity.FlashcardSession;
import com.japaneselearning.flashcard.entity.FlashcardSessionItem;
import com.japaneselearning.flashcard.repository.FlashcardReviewRepository;
import com.japaneselearning.flashcard.repository.FlashcardSessionRepository;
import com.japaneselearning.flashcard.repository.VocabularyProgressRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@ApplicationScoped
public class FlashcardReviewService {
    private static final Logger LOG = Logger.getLogger(FlashcardReviewService.class);

    private final FlashcardSessionService sessions;
    private final FlashcardSessionRepository sessionRepository;
    private final FlashcardReviewRepository reviews;
    private final VocabularyProgressRepository progress;
    private final VocabularyProgressService progressService;
    private final SpacedRepetitionService srs;
    private final FlashcardTime time;
    private final ObjectMapper json;

    public FlashcardReviewService(
            FlashcardSessionService sessions,
            FlashcardSessionRepository sessionRepository,
            FlashcardReviewRepository reviews,
            VocabularyProgressRepository progress,
            VocabularyProgressService progressService,
            SpacedRepetitionService srs,
            FlashcardTime time,
            ObjectMapper json
    ) {
        this.sessions = sessions;
        this.sessionRepository = sessionRepository;
        this.reviews = reviews;
        this.progress = progress;
        this.progressService = progressService;
        this.srs = srs;
        this.time = time;
        this.json = json;
    }

    @WithSession
    @LogOperation(
            value = "flashcard.review",
            description = "Flashcard review submission"
    )
    public Uni<FlashcardReviewResponse> submit(
            String subject,
            Long sessionId,
            Long itemId,
            FlashcardReviewRequest request
    ) {
        // Translate failures after transaction completion, including flush/commit failures.
        return Panache.withTransaction(() -> sessions.owned(subject, sessionId, true)
                        .chain(session -> sessionRepository.items(sessionId).chain(items -> {
                            FlashcardSessionItem item = items.stream()
                                    .filter(candidate -> candidate.id.equals(itemId))
                                    .findFirst()
                                    .orElseThrow(() -> {
                                        decision(
                                                sessionId,
                                                itemId,
                                                "item_not_in_session",
                                                "Review cannot be accepted because the item does not belong to the owned learning session"
                                        );

                                        return new ResourceNotFoundException(
                                                "FLASHCARD_ITEM_NOT_FOUND",
                                                "Session item not found"
                                        );
                                    });

                            return reviews.findItem(itemId).chain(existing -> {
                                if (existing != null)
                                    return replay(existing, request);

                                if (session.status != FlashcardSessionStatus.IN_PROGRESS
                                        || item.position != session.completedCount
                                        || item.completedAt != null
                                ) {
                                    decision(
                                            sessionId,
                                            itemId,
                                            "session_or_item_state",
                                            "Review cannot be accepted: session status=" + session.status
                                                    + ", item position=" + item.position
                                                    + ", next position=" + session.completedCount
                                                    + ", item already completed=" + (item.completedAt != null)
                                    );

                                    throw FlashcardConflicts.changed();
                                }

                                return accept(subject, session, item, request, time.now());
                            });
                        }))
                )
                .onFailure(FlashcardConflicts::concurrent)
                .transform(failure -> FlashcardConflicts.changed());
    }

    private Uni<FlashcardReviewResponse> replay(
            FlashcardReview review,
            FlashcardReviewRequest request
    ) {
        if (review.rating != request.rating()
                || review.expectedProgressVersion != request.expectedProgressVersion()
        ) {
            decision(
                    null,
                    review.sessionItemId,
                    "replay_mismatch",
                    "Review retry conflicts with the previously recorded rating or expected progress version"
            );

            throw FlashcardConflicts.changed();
        }

        return OperationLog.outcome(
                Uni.createFrom().item(response(review)),
                "replayed",
                "Returned the previously committed review for itemId={itemId}; no review or progress was written",
                Map.of(
                        "itemId", review.sessionItemId,
                        "vocabularyId", review.vocabularyId,
                        "reason", "idempotent_replay"
                )
        );
    }

    private Uni<FlashcardReviewResponse> accept(
            String subject,
            FlashcardSession session,
            FlashcardSessionItem item,
            FlashcardReviewRequest request,
            Instant now
    ) {
        return progress.findOwned(subject, item.vocabularyId).chain(row -> {
            ProgressState before = VocabularyProgressService.state(row);

            boolean scheduling = session.mode.scheduling();

            StructuredLog.event(
                    LOG,
                    Logger.Level.DEBUG,
                    "flashcard.review.mode",
                    scheduling ? "Validating due time and progress version before calculating the SRS schedule"
                            : "Processing a practice review without modifying the SRS schedule",
                    Map.of(
                            "operation", "flashcard.review",
                            "sessionId", session.id,
                            "itemId", item.id,
                            "vocabularyId", item.vocabularyId,
                            "reason", scheduling ? "scheduling" : "practice"
                    )
            );

            if (scheduling && before.nextReviewAt() != null && before.nextReviewAt().isAfter(now)) {
                decision(
                        session.id,
                        item.id,
                        "not_due",
                        "Review cannot be accepted because the vocabulary is not due for scheduled review"
                );

                throw new ConflictException(
                        "FLASHCARD_NOT_DUE",
                        "Vocabulary is not due; refresh the session or abandon it and start another"
                );
            }

            if (scheduling && before.version() != request.expectedProgressVersion()) {
                decision(
                        session.id,
                        item.id,
                        "progress_version_mismatch",
                        "Review cannot be accepted because learning progress changed since the session item was retrieved"
                );

                throw FlashcardConflicts.changed();
            }

            ProgressState after = scheduling
                    ? srs.review(before, request.rating(), now)
                    : before;

            Uni<Void> save = scheduling
                    ? progressService.save(subject, item.vocabularyId, row, after, now)
                    : Uni.createFrom().voidItem();

            return save.chain(() -> {
                FlashcardReview review = new FlashcardReview();
                review.sessionItemId = item.id;
                review.userSubject = subject;
                review.vocabularyId = item.vocabularyId;
                review.rating = request.rating();
                review.scheduling = scheduling;
                review.expectedProgressVersion = request.expectedProgressVersion();
                review.algorithmVersion = scheduling ? SpacedRepetitionService.ALGORITHM_VERSION : "practice-v1";
                review.beforeState = encode(before);
                review.afterState = encode(after);
                review.reviewedAt = FlashcardTime.stored(now);
                item.completedAt = review.reviewedAt;
                session.completedCount++;
                session.updatedAt = review.reviewedAt;

                if (session.completedCount == session.itemCount) {
                    session.status = FlashcardSessionStatus.COMPLETED;
                    session.completedAt = review.reviewedAt;
                }

                return reviews.persistAndFlush(review).map(this::response)
                        .chain(result -> OperationLog.outcome(
                                Uni.createFrom().item(result),
                                "accepted",
                                "Successfully recorded flashcard review for sessionId={sessionId}, itemId={itemId}"
                                        + (scheduling
                                        ? "; updated learning progress was saved"
                                        : "; practice left the SRS schedule unchanged")
                                        + (session.status == FlashcardSessionStatus.COMPLETED
                                        ? "; successfully completed the learning session"
                                        : "; learning session remains in progress"),
                                Map.of(
                                        "sessionId", session.id,
                                        "itemId", item.id,
                                        "vocabularyId", item.vocabularyId,
                                        "reason", request.rating().name(),
                                        "learning.status", after.status().name(),
                                        "intervalSeconds", after.intervalSeconds(),
                                        "repetitions", after.successfulRepetitionCount(),
                                        "count", session.completedCount,
                                        "total", session.itemCount
                                )
                        ));
            });
        });
    }

    private void decision(Long sessionId, Long itemId, String reason, String message) {
        if (LOG.isDebugEnabled()) {
            var fields = new HashMap<String, Object>();

            fields.put("operation", "flashcard.review");
            fields.put("itemId", itemId);
            fields.put("reason", reason);

            if (sessionId != null)
                fields.put("sessionId", sessionId);

            StructuredLog.event(
                    LOG,
                    Logger.Level.DEBUG,
                    "flashcard.review.validation_failed",
                    message,
                    fields
            );
        }
    }

    private String encode(ProgressState state) {
        try {
            return json.writeValueAsString(state);
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Could not serialize flashcard progress",
                    failure
            );
        }
    }

    private FlashcardReviewResponse response(FlashcardReview review) {
        try {
            return new FlashcardReviewResponse(
                    review.id,
                    review.sessionItemId,
                    review.vocabularyId,
                    review.rating,
                    review.scheduling,
                    json.readValue(review.afterState, ProgressState.class),
                    FlashcardTime.exposed(review.reviewedAt)
            );
        } catch (JsonProcessingException failure) {
            throw new IllegalStateException(
                    "Could not read stored flashcard progress",
                    failure
            );
        }
    }
}
