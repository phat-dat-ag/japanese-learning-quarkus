package com.japaneselearning.flashcard.service;

import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.logging.StructuredLog;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import org.hibernate.StaleStateException;
import org.hibernate.exception.ConstraintViolationException;
import org.hibernate.exception.LockAcquisitionException;
import org.jboss.logging.Logger;

import java.util.Map;

final class FlashcardConflicts {
    private static final Logger LOG = Logger.getLogger(FlashcardConflicts.class);

    private FlashcardConflicts() {
    }

    static boolean concurrent(Throwable failure) {
        for (Throwable cause = failure; cause != null; cause = cause.getCause()) {
            if (cause instanceof OptimisticLockException
                    || cause instanceof StaleStateException
                    || cause instanceof PessimisticLockException
                    || cause instanceof LockAcquisitionException
            ) {
                detected(cause);

                return true;
            }

            if (cause instanceof ConstraintViolationException constraint
                    && constraint.getErrorCode() == 1062
            ) {
                detected(cause);

                return true;
            }
        }

        return false;
    }

    private static void detected(Throwable cause) {
        StructuredLog.event(
                LOG,
                Logger.Level.DEBUG,
                "flashcard.concurrency.detected",
                "Detected a concurrent flashcard write failure; the transaction did not complete successfully",
                Map.of(
                        "reason", "concurrent_write",
                        "error.type", cause.getClass().getName(),
                        "outcome", "failure"
                )
        );
    }

    static ConflictException changed() {
        return new ConflictException(
                "FLASHCARD_CONFLICT",
                "Learning state changed; refresh before retrying"
        );
    }
}
