package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.flashcard.domain.ProgressState;
import com.japaneselearning.flashcard.entity.VocabularyProgress;
import com.japaneselearning.flashcard.repository.VocabularyProgressRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@ApplicationScoped
public class VocabularyProgressService {
    private static final Logger LOG = Logger.getLogger(VocabularyProgressService.class);

    private final VocabularyProgressRepository progress;

    public VocabularyProgressService(VocabularyProgressRepository progress) {
        this.progress = progress;
    }

    public Uni<Map<Long, ProgressState>> states(String subject, List<Long> ids) {
        return progress.findOwned(subject, ids)
                .map(rows -> rows.stream()
                        .collect(Collectors.toMap(row ->
                                        row.vocabularyId,
                                VocabularyProgressService::state
                        ))
                ).invoke(states -> StructuredLog.event(
                        LOG,
                        Logger.Level.DEBUG,
                        "flashcard.progress.loaded",
                        "Retrieved stored SRS progress for " + states.size()
                                + " of " + ids.size()
                                + " requested vocabulary entries; entries without progress use the NEW state",
                        Map.of(
                                "count", states.size(),
                                "total", ids.size(), "outcome", "success"
                        )
                ));
    }

    public static ProgressState state(VocabularyProgress row) {
        if (row == null) {
            return ProgressState.unseen();
        }

        // Public revision zero denotes absence; Hibernate's first persisted version is zero.
        return new ProgressState(
                row.status,
                row.successfulRepetitionCount,
                row.lapseCount,
                row.intervalSeconds,
                row.easeFactor,
                FlashcardTime.exposed(row.lastReviewedAt),
                FlashcardTime.exposed(row.nextReviewAt),
                FlashcardTime.exposed(row.masteredAt),
                row.version + 1
        );
    }

    public Uni<Void> save(
            String subject,
            Long vocabularyId,
            VocabularyProgress row,
            ProgressState state,
            Instant now
    ) {
        VocabularyProgress target = row == null ? new VocabularyProgress() : row;

        target.userSubject = subject;
        target.vocabularyId = vocabularyId;
        target.status = state.status();
        target.successfulRepetitionCount = state.successfulRepetitionCount();
        target.lapseCount = state.lapseCount();
        target.intervalSeconds = state.intervalSeconds();
        target.easeFactor = state.easeFactor();
        target.lastReviewedAt = FlashcardTime.stored(state.lastReviewedAt());
        target.nextReviewAt = FlashcardTime.stored(state.nextReviewAt());
        target.masteredAt = FlashcardTime.stored(state.masteredAt());
        target.updatedAt = FlashcardTime.stored(now);

        if (row == null) {
            target.createdAt = target.updatedAt;
            return progress.persist(target).replaceWithVoid();
        }

        return Uni.createFrom().voidItem();
    }
}
