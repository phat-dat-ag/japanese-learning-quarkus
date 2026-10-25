package com.japaneselearning.flashcard.service;

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
                                    .orElseThrow(() -> new ResourceNotFoundException(
                                            "FLASHCARD_ITEM_NOT_FOUND",
                                            "Session item not found"
                                    ));

                            return reviews.findItem(itemId).chain(existing -> {
                                if (existing != null)
                                    return replay(existing, request);

                                if (session.status != FlashcardSessionStatus.IN_PROGRESS
                                        || item.position != session.completedCount
                                        || item.completedAt != null
                                ) {
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
            throw FlashcardConflicts.changed();
        }

        return Uni.createFrom().item(response(review));
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

            if (scheduling && before.nextReviewAt() != null && before.nextReviewAt().isAfter(now)) {
                throw new ConflictException(
                        "FLASHCARD_NOT_DUE",
                        "Vocabulary is not due; refresh the session or abandon it and start another"
                );
            }

            if (scheduling && before.version() != request.expectedProgressVersion()) {
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

                return reviews.persistAndFlush(review).map(this::response);
            });
        }).invoke(result -> LOG.debugf(
                "Flashcard review accepted sessionId=%d itemId=%d scheduling=%s",
                new Object[]{session.id, item.id, result.scheduling()}
        ));
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
