package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.Kanji;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class KanjiRepository
        implements PanacheRepository<Kanji> {

    public Uni<Kanji> findByCharacter(String character) {
        return find("character", character).firstResult();
    }
}