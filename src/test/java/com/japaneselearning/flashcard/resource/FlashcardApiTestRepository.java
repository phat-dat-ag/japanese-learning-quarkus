package com.japaneselearning.flashcard.resource;

import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.persistence.NoResultException;
import jakarta.ws.rs.ServiceUnavailableException;

import java.util.List;

@Alternative
@ApplicationScoped
public class FlashcardApiTestRepository extends FlashcardRepository {

    @Override
    public Uni<List<Vocabulary>> findVocabulary(
            String levelCode, Integer lessonNumber, int offset, int limit
    ) {
        Vocabulary vocabulary = new Vocabulary();
        vocabulary.id = 42L;
        vocabulary.word = "word";
        return Uni.createFrom().item(List.of(vocabulary));
    }

    @Override
    public Uni<Long> countVocabulary(String levelCode, Integer lessonNumber) {
        return Uni.createFrom().item(1L);
    }

    @Override
    public Uni<Vocabulary> findVocabularyById(Long vocabularyId) {
        if (vocabularyId == 500L) {
            return Uni.createFrom().failure(new IllegalStateException("Database connection details"));
        }
        if (vocabularyId == 503L) {
            return Uni.createFrom().failure(new ServiceUnavailableException("Internal service details"));
        }
        return Uni.createFrom().failure(new NoResultException("Missing vocabulary"));
    }
}
