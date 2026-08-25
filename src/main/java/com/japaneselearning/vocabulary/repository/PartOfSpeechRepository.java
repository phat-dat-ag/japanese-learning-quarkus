package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.PartOfSpeech;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class PartOfSpeechRepository
        implements PanacheRepository<PartOfSpeech> {

    public Uni<PartOfSpeech> findByCode(String code) {
        return find("code", code).firstResult();
    }
}