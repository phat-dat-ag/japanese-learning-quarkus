package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class FlashcardValidation {
    private FlashcardValidation() {
    }

    public static void pagination(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw invalid(
                    "page",
                    "Page must be nonnegative, size 1-100, and offset within integer range"
            );
        }
    }

    public static String level(String level) {
        if (level == null) {
            return null;
        }

        String normalized = level.trim().toUpperCase(Locale.ROOT);

        if (!Set.of("N1", "N2", "N3", "N4", "N5").contains(normalized)) {
            throw invalid("level", "Level must be N1, N2, N3, N4 or N5");
        }

        return normalized;
    }

    public static ValidationException invalid(String field, String message) {
        return new ValidationException(
                "FLASHCARD_VALIDATION_ERROR",
                "Invalid flashcard request",
                List.of(new ValidationError(field, message))
        );
    }
}
