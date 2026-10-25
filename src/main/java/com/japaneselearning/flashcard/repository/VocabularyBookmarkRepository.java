package com.japaneselearning.flashcard.repository;

import com.japaneselearning.flashcard.entity.VocabularyBookmark;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.util.List;

@ApplicationScoped
public class VocabularyBookmarkRepository implements PanacheRepository<VocabularyBookmark> {
    public Uni<List<VocabularyBookmark>> findOwned(String subject, List<Long> ids) {
        if (ids.isEmpty()) {
            return Uni.createFrom().item(List.of());
        }

        return list("userSubject = ?1 and vocabularyId in ?2", subject, ids);
    }

    public Uni<Integer> add(String subject, Long vocabularyId, LocalDateTime now) {
        // A no-op duplicate update preserves the original bookmark timestamp under concurrency.
        return Panache.getSession().chain(session -> session.createNativeQuery("""
                                INSERT INTO user_vocabulary_bookmarks (user_subject, vocabulary_id, created_at)
                                VALUES (:subject, :vocabulary, :now) ON DUPLICATE KEY UPDATE id = id
                                """
                        )
                        .setParameter("subject", subject)
                        .setParameter("vocabulary", vocabularyId)
                        .setParameter("now", now)
                        .executeUpdate()
        );
    }

    public Uni<Long> remove(String subject, Long vocabularyId) {
        return delete("userSubject = ?1 and vocabularyId = ?2", subject, vocabularyId);
    }
}
