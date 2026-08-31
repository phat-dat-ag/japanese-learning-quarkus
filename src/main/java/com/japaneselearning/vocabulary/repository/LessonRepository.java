package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.Lesson;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class LessonRepository
        implements PanacheRepository<Lesson> {

    public Uni<Lesson> findByLevelIdAndLessonNumber(
            Long levelId,
            Integer lessonNumber) {

        return find(
                "levelId = ?1 and lessonNumber = ?2",
                levelId,
                lessonNumber
        ).firstResult();
    }
}