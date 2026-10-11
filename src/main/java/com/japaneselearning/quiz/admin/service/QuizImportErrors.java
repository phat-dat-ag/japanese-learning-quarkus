package com.japaneselearning.quiz.admin.service;

import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.quiz.admin.dto.QuizImportError;

import java.util.List;

final class QuizImportErrors {
    private QuizImportErrors() {
    }

    static ValidationException invalid(String field, String message) {
        return new ValidationException(
                "QUIZ_IMPORT_INVALID", "Invalid quiz import; no questions processed",
                List.of(new ValidationError(field, message))
        );
    }

    static List<QuizImportError> indexed(int index, BusinessException failure) {
        if (failure instanceof ValidationException validation && !validation.getErrors().isEmpty()) {
            return validation.getErrors().stream().map(error -> new QuizImportError(
                    domainPath(index, error.field()), failure.getCode(), error.message()
            )).toList();
        }

        return List.of(new QuizImportError(
                "[" + index + "].content",
                failure.getCode(),
                failure.getMessage()
        ));
    }

    private static String domainPath(int index, String field) {
        String prefix = "[" + index + "].";

        if (field.equals("options")) {
            return prefix + field;
        }

        String property = switch (field) {
            case "source" -> "sourceType";
            case "target" -> "targetStart";
            case "example" -> "exampleId";
            default -> field;
        };

        return prefix + "content." + property;
    }
}
