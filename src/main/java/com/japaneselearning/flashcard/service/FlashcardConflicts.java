package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.exception.ConflictException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;

final class FlashcardConflicts {
    private FlashcardConflicts() {
    }

    static boolean concurrent(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException
                    || cause instanceof StaleStateException
                    || cause instanceof PessimisticLockException
                    || cause instanceof LockAcquisitionException
            ) {
                return true;
            }

            if (cause instanceof ConstraintViolationException constraint
                    && constraint.getErrorCode() == 1062
            ) {
                return true;
            }
        }

        return false;
    }

    static ConflictException changed() {
        return new ConflictException(
                "FLASHCARD_CONFLICT",
                "Learning state changed; refresh before retrying"
        );
    }
}
