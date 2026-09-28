package com.japaneselearning.vocabulary.importer;

import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.vocabulary.entity.Lesson;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.importer.dto.VocabularyImportItem;
import com.japaneselearning.vocabulary.repository.LessonVocabularyRepository;
import com.japaneselearning.vocabulary.repository.VocabularyLevelRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

@ApplicationScoped
public class VocabularyAssignmentValidator {
    private static final Logger LOG = Logger.getLogger(VocabularyAssignmentValidator.class);
    private final VocabularyLevelRepository levels;
    private final LessonVocabularyRepository lessons;

    public VocabularyAssignmentValidator(VocabularyLevelRepository levels, LessonVocabularyRepository lessons) {
        this.levels = levels;
        this.lessons = lessons;
    }

    public Uni<Void> validate(Vocabulary vocabulary, VocabularyImportItem item) {
        // Queries share one reactive session and must run sequentially.
        return levels.findLevelCodes(vocabulary.id)
                .flatMap(existingLevels -> lessons.findLessons(vocabulary.id)
                        .invoke(existingLessons -> compare(vocabulary, item, existingLevels, existingLessons)))
                .replaceWithVoid();
    }

    private void compare(Vocabulary vocabulary, VocabularyImportItem item,
                         List<String> existingLevels, List<Lesson> existingLessons) {
        Set<String> requestedLevels = new TreeSet<>(item.levels);
        Set<String> storedLevels = new TreeSet<>(existingLevels);
        Set<String> requestedLessons = item.lessons.stream()
                .map(lesson -> lesson.level + "/" + lesson.lessonNumber)
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> storedLessons = existingLessons.stream()
                .map(lesson -> lesson.level.code + "/" + lesson.lessonNumber)
                .collect(Collectors.toCollection(TreeSet::new));

        List<ValidationError> errors = new ArrayList<>();
        if (!storedLevels.equals(requestedLevels)) {
            errors.add(new ValidationError("levels",
                    "Existing levels: " + storedLevels + "; requested levels: " + requestedLevels));
        }
        if (!storedLessons.equals(requestedLessons)) {
            errors.add(new ValidationError("lessons",
                    "Existing lessons: " + storedLessons + "; requested lessons: " + requestedLessons));
        }
        if (!errors.isEmpty()) {
            LOG.debugf("Vocabulary assignment mismatch levels=%s lessons=%s",
                    !storedLevels.equals(requestedLevels), !storedLessons.equals(requestedLessons));
            throw new ValidationException("VALIDATION_ERROR",
                    "Vocabulary '" + vocabulary.word + "' already exists but its assignments do not match the request",
                    errors);
        }
    }
}
