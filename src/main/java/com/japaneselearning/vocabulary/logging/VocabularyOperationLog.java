package com.japaneselearning.vocabulary.logging;

import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.vocabulary.dto.LessonBatchResponse;
import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.jboss.logging.Logger;

import java.lang.reflect.Parameter;
import java.util.List;
import java.util.Set;

@LogVocabularyOperation
@Interceptor
// WithTransaction has priority PLATFORM_BEFORE + 200: observe its completed Uni, not a pending write.
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 199)
public class VocabularyOperationLog {
    private static final Logger LOG = Logger.getLogger(VocabularyOperationLog.class);
    private static final Object ACTIVE_OPERATION = new Object();
    private static final Set<String> ID_PARAMETERS = Set.of(
            "vocabularyId", "lessonId", "levelId", "readingId", "meaningId", "exampleId", "pitchAccentId", "kanjiId"
    );

    @AroundInvoke
    Object log(InvocationContext invocation) throws Exception {
        String operation = invocation.getMethod().getAnnotation(LogVocabularyOperation.class).value();
        String identifiers = identifiers(invocation);

        Uni<?> result = (Uni<?>) invocation.proceed();

        return result.withContext((uni, context) -> {
            // Nested lesson creates belong to the outer batch summary, not per-record INFO/WARN logs.
            if (context.contains(ACTIVE_OPERATION)) {
                return uni;
            }

            context.put(ACTIVE_OPERATION, true);

            return uni.invoke(item -> completed(operation, identifiers, item))
                    .onFailure(BusinessException.class)
                    .invoke(failure -> rejected(operation, identifiers, (BusinessException) failure))
                    .onTermination().invoke(() -> context.delete(ACTIVE_OPERATION));
        });
    }

    private String identifiers(InvocationContext invocation) {
        StringBuilder identifiers = new StringBuilder();
        Parameter[] parameters = invocation.getMethod().getParameters();
        Object[] arguments = invocation.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            // Deliberate allowlist: never stringify requests, file paths, collections, or arbitrary arguments.
            if (ID_PARAMETERS.contains(parameters[i].getName()) && arguments[i] instanceof Long id) {
                identifiers.append(' ').append(parameters[i].getName()).append('=').append(id);
            }
        }

        return identifiers.toString();
    }

    private void completed(String operation, String identifiers, Object result) {
        if (result instanceof LessonBatchResponse batch) {
            LOG.logf(batch.failed() > 0 ? Logger.Level.WARN : Logger.Level.INFO,
                    "Business operation completed operation=%s total=%d succeeded=%d failed=%d",
                    operation, batch.total(), batch.succeeded(), batch.failed()
            );
        } else if (result instanceof ImportResult batch) {
            LOG.infof(
                    "Business operation completed operation=%s total=%d created=%d updated=%d",
                    operation, batch.total(), batch.created(), batch.updated()
            );
        } else if (result instanceof List<?> items) {
            LOG.infof("Business operation completed operation=%s%s count=%d", operation, identifiers, items.size());
        } else if (result instanceof LessonResponse lesson) {
            LOG.infof("Business operation completed operation=%s lessonId=%d", operation, lesson.id());
        } else {
            LOG.infof("Business operation completed operation=%s%s", operation, identifiers);
        }
    }

    private void rejected(String operation, String identifiers, BusinessException failure) {
        String reason = failure instanceof ConflictException ? "conflict"
                : failure instanceof ResourceNotFoundException ? "not_found" : "validation";
        Logger.Level level = failure instanceof ConflictException || operation.equals("vocabulary.import")
                ? Logger.Level.WARN : Logger.Level.DEBUG;
        LOG.logf(level, "Business operation rejected operation=%s%s reason=%s", operation, identifiers, reason);
        // Unexpected failures remain exclusively with SafeExceptionLog / BatchItemErrors.
    }
}
