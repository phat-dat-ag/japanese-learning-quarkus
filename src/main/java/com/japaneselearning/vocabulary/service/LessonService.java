package com.japaneselearning.vocabulary.service;

import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.NotFoundException;

import java.util.List;

@ApplicationScoped
public class LessonService {

    private final LessonRepository lessonRepository;
    private final JlptLevelRepository jlptLevelRepository;

    public LessonService(
            LessonRepository lessonRepository,
            JlptLevelRepository jlptLevelRepository
    ) {
        this.lessonRepository = lessonRepository;
        this.jlptLevelRepository = jlptLevelRepository;
    }

    @WithSession
    public Uni<List<LessonResponse>> getLessonsByLevel(String level) {

        return jlptLevelRepository
                .findByCode(level)
                .onItem()
                .ifNull()
                .failWith(() ->
                        new NotFoundException(
                                "JLPT level not found: " + level
                        )
                )
                .chain(jlptLevel ->
                        lessonRepository
                                .findByLevelId(jlptLevel.id)
                )
                .map(lessons ->
                        lessons.stream()
                                .map(lesson ->
                                        new LessonResponse(
                                                lesson.id,
                                                lesson.lessonNumber,
                                                lesson.title,
                                                lesson.description
                                        )
                                )
                                .toList()
                );
    }
}