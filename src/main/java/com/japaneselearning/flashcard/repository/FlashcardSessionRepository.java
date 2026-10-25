package com.japaneselearning.flashcard.repository;

import com.japaneselearning.flashcard.entity.FlashcardSession;
import com.japaneselearning.flashcard.entity.FlashcardSessionItem;
import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;

@ApplicationScoped
public class FlashcardSessionRepository implements PanacheRepository<FlashcardSession> {
    public Uni<FlashcardSession> owned(String subject, Long id, boolean lock) {
        var query = find("userSubject = ?1 and id = ?2", subject, id);

        return (lock
                ? query.withLock(LockModeType.PESSIMISTIC_WRITE)
                : query
        ).firstResult();
    }

    public Uni<List<FlashcardSessionItem>> items(Long id) {
        return Panache.getSession().chain(session -> session.createQuery(
                        "from FlashcardSessionItem where sessionId = :id order by position",
                        FlashcardSessionItem.class
                )
                .setParameter("id", id)
                .getResultList());
    }

    public Uni<Void> persistItems(List<FlashcardSessionItem> items) {
        return Panache.getSession().chain(session -> session.persistAll(items.toArray()));
    }
}
