package com.japaneselearning.vocabulary.service;

import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;

import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;

import jakarta.enterprise.context.ApplicationScoped;

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
                        new ResourceNotFoundException(
                                "JLPT_LEVEL_NOT_FOUND",
                                "JLPT level " + level + " not found"
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