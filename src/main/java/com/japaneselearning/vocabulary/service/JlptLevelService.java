package com.japaneselearning.vocabulary.service;

import com.japaneselearning.vocabulary.dto.JlptLevelResponse;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.List;

@ApplicationScoped
public class JlptLevelService {
    private static final Logger LOG = Logger.getLogger(JlptLevelService.class);

    private final JlptLevelRepository jlptLevelRepository;

    public JlptLevelService(JlptLevelRepository jlptLevelRepository) {
        this.jlptLevelRepository = jlptLevelRepository;
    }

    @WithSession
    public Uni<List<JlptLevelResponse>> getLevels() {
        return jlptLevelRepository.findAllOrdered()
                .map(levels -> levels.stream()
                        .map(level -> new JlptLevelResponse(level.code, level.name))
                        .toList())
                .invoke(levels -> LOG.debugf("JLPT levels loaded count=%d", levels.size()));
    }
}
