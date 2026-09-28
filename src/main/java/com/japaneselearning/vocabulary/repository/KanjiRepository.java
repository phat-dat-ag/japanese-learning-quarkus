package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.Kanji;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

@ApplicationScoped
public class KanjiRepository implements PanacheRepository<Kanji> {

    public Uni<Kanji> findByCharacter(String character) {
        return find("character", character).firstResult();
    }

    public Uni<Kanji> findKanjiByIdForUpdate(Long kanjiId) {
        return find("id", kanjiId).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }

    public Uni<Kanji> findKanjiByCharacterForUpdate(String character) {
        return find("character", character).withLock(LockModeType.PESSIMISTIC_WRITE).firstResult();
    }
}
