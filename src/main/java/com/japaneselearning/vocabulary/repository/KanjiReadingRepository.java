package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.KanjiReading;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class KanjiReadingRepository
        implements PanacheRepository<KanjiReading> {
}