package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.LessonContent;
import io.quarkus.hibernate.reactive.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LessonContentRepository implements PanacheRepositoryBase<LessonContent, Long> {
}
