package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.logging.LogOperation;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.flashcard.repository.VocabularyBookmarkRepository;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.NoResultException;

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
    @LogOperation(value = "flashcard.bookmark.add")
    public Uni<BookmarkResponse> add(String subject, Long vocabularyId) {
        return Panache.withTransaction(() -> vocabulary.findVocabularyById(vocabularyId)
                        .onFailure(NoResultException.class).transform(failure -> new ResourceNotFoundException(
                                "VOCABULARY_NOT_FOUND",
                                "Vocabulary not found"
                        ))
                        .chain(() -> bookmarks.add(subject, vocabularyId, FlashcardTime.stored(time.now())))
                        .replaceWith(new BookmarkResponse(vocabularyId, true)))
                .onFailure(FlashcardConflicts::concurrent).transform(failure -> FlashcardConflicts.changed());
    }

    @WithSession
    @LogOperation(value = "flashcard.bookmark.remove")
    public Uni<BookmarkResponse> remove(String subject, Long vocabularyId) {
        return Panache.withTransaction(() -> bookmarks.remove(subject, vocabularyId)
                        .replaceWith(new BookmarkResponse(vocabularyId, false))
                )
                .onFailure(FlashcardConflicts::concurrent)
                .transform(failure -> FlashcardConflicts.changed());
    }
}
