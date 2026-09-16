package com.japaneselearning.security;

import com.japaneselearning.flashcard.dto.FlashcardDetailResponse;
import com.japaneselearning.flashcard.dto.FlashcardListResponse;
import com.japaneselearning.flashcard.dto.VocabularyResponse;
import com.japaneselearning.flashcard.repository.FlashcardRepository;
import com.japaneselearning.flashcard.service.FlashcardService;
import com.japaneselearning.vocabulary.dto.JlptLevelResponse;
import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import com.japaneselearning.vocabulary.importer.VocabularyImporter;
import com.japaneselearning.vocabulary.repository.JlptLevelRepository;
import com.japaneselearning.vocabulary.repository.LessonRepository;
import com.japaneselearning.vocabulary.service.JlptLevelService;
import com.japaneselearning.vocabulary.service.LessonService;
import com.japaneselearning.vocabulary.service.VocabularyService;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.inject.Alternative;
import jakarta.inject.Singleton;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public class JwtSecurityTestProfile implements QuarkusTestProfile {

    @Override
    public Set<Class<?>> getEnabledAlternatives() {
        return Set.of(TestLevelService.class, TestVocabularyService.class, TestLessonService.class, TestFlashcardService.class);
    }

    // Keep real resources and security interceptors; isolate database business operations.
    @Alternative
    @Singleton
    public static class TestLevelService extends JlptLevelService {

        public TestLevelService(JlptLevelRepository repository) {
            super(repository);
        }

        @Override
        public Uni<List<JlptLevelResponse>> getLevels() {
            return Uni.createFrom().item(List.of(new JlptLevelResponse("N5", "Beginner")));
        }
    }

    @Alternative
    @Singleton
    public static class TestVocabularyService extends VocabularyService {

        public TestVocabularyService(VocabularyImporter importer) {
            super(importer);
        }

        @Override
        public Uni<ImportResult> importVocabulary(Path file) {
            return Uni.createFrom().item(new ImportResult(1, 1, 0));
        }
    }

    @Alternative
    @Singleton
    public static class TestLessonService extends LessonService {

        public TestLessonService(LessonRepository lessons, JlptLevelRepository levels) {
            super(lessons, levels);
        }

        @Override
        public Uni<List<LessonResponse>> getLessonsByLevel(String level) {
            return Uni.createFrom().item(List.of(new LessonResponse(1L, 1, "Lesson 1", "Test lesson")));
        }
    }

    @Alternative
    @Singleton
    public static class TestFlashcardService extends FlashcardService {

        public TestFlashcardService(FlashcardRepository repository) {
            super(repository);
        }

        @Override
        public Uni<FlashcardListResponse> getFlashcards(String level, Integer lesson, int page, int size) {
            return Uni.createFrom().item(new FlashcardListResponse(List.of(), page, size, 0, 0));
        }

        @Override
        public Uni<FlashcardDetailResponse> getFlashcard(Long id) {
            return Uni.createFrom().item(new FlashcardDetailResponse(
                    new VocabularyResponse(id, "test", "test"), List.of(), List.of(), List.of(),
                    List.of(), List.of(), List.of(), List.of()));
        }
    }
}
