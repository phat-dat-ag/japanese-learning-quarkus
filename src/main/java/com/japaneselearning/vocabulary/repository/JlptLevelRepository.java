package com.japaneselearning.vocabulary.repository;

import com.japaneselearning.vocabulary.entity.JlptLevel;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.LockModeType;

import java.util.List;

@ApplicationScoped
public class JlptLevelRepository implements PanacheRepository<JlptLevel> {

    public Uni<JlptLevel> findByIdForUpdate(Long levelId) {
        return find("id", levelId)
                .withLock(LockModeType.PESSIMISTIC_WRITE)
                .firstResult();
    }

    public Uni<JlptLevel> findByCode(String code) {
        return find("code", code).firstResult();
    }

    public Uni<List<JlptLevel>> findAllOrdered() {
        return find("order by displayOrder").list();
    }
}
