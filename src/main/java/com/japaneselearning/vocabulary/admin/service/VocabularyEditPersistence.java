package com.japaneselearning.vocabulary.admin.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.vocabulary.entity.Vocabulary;
import com.japaneselearning.vocabulary.repository.VocabularyRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.hibernate.exception.ConstraintViolationException;

import java.util.List;
import java.util.Set;

@ApplicationScoped
public class VocabularyEditPersistence {
    private static final Set<String> MAPPED_UNIQUE_CONSTRAINTS = Set.of(
            "uk_vocabulary_normalized_word",
            "uk_vocabulary_readings",
            "uk_kanji_character",
            "uk_kanji_readings",
            "uk_pitch_accents_reading_pattern"
    );

    private final VocabularyRepository vocabularies;

    public VocabularyEditPersistence(VocabularyRepository vocabularies) {
        this.vocabularies = vocabularies;
    }

    public Uni<Vocabulary> requireVocabularyForUpdate(Long vocabularyId) {
        return requireFound(vocabularies.findVocabularyByIdForUpdate(vocabularyId), "Vocabulary");
    }

    public <T> Uni<T> requireFound(Uni<T> lookup, String resourceName) {
        return lookup.onItem().ifNull().failWith(() -> resourceNotFound(resourceName));
    }

    public <T> Uni<T> flushAndMapUniqueConflicts(Uni<T> editOperation) {
        return editOperation.call(vocabularies::flush)
                .onFailure(failure -> failure instanceof ConstraintViolationException violation
                        && isMappedUniqueConstraint(violation.getConstraintName()))
                .transform(failure -> conflict("The requested value already exists"));
    }

    private boolean isMappedUniqueConstraint(String constraintName) {
        return constraintName != null && MAPPED_UNIQUE_CONSTRAINTS.stream()
                .anyMatch(constraint -> constraintName.equals(constraint)
                        || constraintName.endsWith("." + constraint));
    }

    public static ResourceNotFoundException resourceNotFound(String resourceName) {
        return new ResourceNotFoundException(
                "RESOURCE_NOT_FOUND",
                resourceName + " not found for this vocabulary"
        );
    }

    public static ConflictException conflict(String message) {
        return new ConflictException("VOCABULARY_EDIT_CONFLICT", message);
    }

    public static ValidationException invalidRequest(String field, String message) {
        return new ValidationException(
                "VALIDATION_ERROR",
                "Invalid vocabulary edit",
                List.of(new ValidationError(field, message))
        );
    }
}
