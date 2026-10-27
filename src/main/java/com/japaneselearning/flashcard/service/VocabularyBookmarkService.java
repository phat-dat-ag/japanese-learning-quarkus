package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.logging.OperationLog;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.flashcard.repository.VocabularyBookmarkRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;

import java.util.Map;

@ApplicationScoped
public class VocabularyBookmarkService {
    public record BookmarkResponse(Long vocabularyId, boolean bookmarked) {
    }

    private final VocabularyBookmarkRepository bookmarks;
    private final FlashcardRepository vocabulary;
    private final FlashcardTime time;

    public VocabularyBookmarkService(
            VocabularyBookmarkRepository bookmarks,
            FlashcardRepository vocabulary,
            FlashcardTime time
    ) {
        this.bookmarks = bookmarks;
        this.vocabulary = vocabulary;
        this.time = time;
    }

    @WithSession
    @LogOperation(
            value = "flashcard.bookmark.add",
            description = "Vocabulary bookmark addition"
    )
    public Uni<BookmarkResponse> add(String subject, Long vocabularyId) {
        // MySQL found-row semantics can report 1 for either an insert or a duplicate.
        // Report the established state without claiming that a new row was inserted.
        return Panache.withTransaction(() -> vocabulary.findVocabularyById(vocabularyId)
                        .onFailure(NoResultException.class).transform(failure -> new ResourceNotFoundException(
                                "VOCABULARY_NOT_FOUND",
                                "Vocabulary not found"
                        ))
                        .chain(() -> bookmarks.add(subject, vocabularyId, FlashcardTime.stored(time.now())))
                        .chain(affected -> OperationLog.outcome(
                                Uni.createFrom().item(new BookmarkResponse(vocabularyId, true)),
                                affected == 0 ? "unchanged" : "completed",
                                affected == 0
                                        ? "Vocabulary bookmark already exists; no bookmark was changed"
                                        : "Successfully ensured vocabulary is bookmarked",
                                Map.of(
                                        "vocabularyId", vocabularyId,
                                        "reason", affected == 0 ? "already_bookmarked" : "bookmark_present"
                                )
                        ))
                )
                .onFailure(FlashcardConflicts::concurrent).transform(failure -> FlashcardConflicts.changed());
    }

    @WithSession
    @LogOperation(
            value = "flashcard.bookmark.remove",
            description = "Vocabulary bookmark removal"
    )
    public Uni<BookmarkResponse> remove(String subject, Long vocabularyId) {
        return Panache.withTransaction(() -> bookmarks.remove(subject, vocabularyId)
                        .chain(removed -> OperationLog.outcome(Uni.createFrom().item(new BookmarkResponse(vocabularyId, false)),
                                removed == 0 ? "unchanged" : "completed",
                                removed == 0
                                        ? "Vocabulary bookmark is already absent; no bookmark was removed"
                                        : "Successfully removed the vocabulary bookmark",
                                Map.of(
                                        "vocabularyId", vocabularyId,
                                        "count", removed,
                                        "reason", removed == 0 ? "already_absent" : "removed"
                                )
                        ))
                )
                .onFailure(FlashcardConflicts::concurrent)
                .transform(failure -> FlashcardConflicts.changed());
    }
}
