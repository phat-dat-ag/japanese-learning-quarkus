package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.JlptLevel;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class JlptLevelRepository
        implements PanacheRepository<JlptLevel> {

    public Uni<JlptLevel> findByCode(String code) {
        return find("code", code).firstResult();
    }
}