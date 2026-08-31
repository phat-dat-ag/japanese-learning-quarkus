package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.Lesson;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

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

    public Uni<List<Lesson>> findByLevelId(Long levelId) {
        return find(
                "levelId = ?1 order by displayOrder",
                levelId
        ).list();
    }
}