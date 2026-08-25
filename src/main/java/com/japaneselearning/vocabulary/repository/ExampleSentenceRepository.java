package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.ExampleSentence;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ExampleSentenceRepository
        implements PanacheRepository<ExampleSentence> {
}