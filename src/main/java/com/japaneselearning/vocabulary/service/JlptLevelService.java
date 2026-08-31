package com.japaneselearning.vocabulary.service;

import com.japaneselearning.vocabulary.dto.JlptLevelResponse;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.List;

@ApplicationScoped
public class JlptLevelService {

    @Inject
    JlptLevelRepository jlptLevelRepository;

    @WithSession
    public Uni<List<JlptLevelResponse>> getLevels() {

        return jlptLevelRepository
                .findAllOrdered()
                .map(levels ->
                        levels.stream()
                                .map(level ->
                                        new JlptLevelResponse(
                                                level.code,
                                                level.name
                                        )
                                )
                                .toList()
                );
    }
}