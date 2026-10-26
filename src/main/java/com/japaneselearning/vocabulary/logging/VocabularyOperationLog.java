package com.japaneselearning.vocabulary.logging;

import com.japaneselearning.common.logging.StructuredLog;
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
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.lang.reflect.Parameter;
import java.util.HashMap;
import java.util.Map;
import java.util.List;
import java.util.Set;

@LogVocabularyOperation
@Interceptor
// WithTransaction has priority PLATFORM_BEFORE + 200: observe its completed Uni, not a pending write.
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 199)
public class VocabularyOperationLog {
    private static final Logger LOG = Logger.getLogger(VocabularyOperationLog.class);
    @ConfigProperty(name = "application.logging.slow-operation-ms", defaultValue = "1000")
    long slowOperationMs = 1000;

    private static final Object ACTIVE_OPERATION = new Object();
    private static final Set<String> ID_PARAMETERS = Set.of(
            "vocabularyId", "lessonId", "levelId", "readingId",
            "meaningId", "exampleId", "pitchAccentId", "kanjiId"
    );

    @AroundInvoke
    Object log(InvocationContext invocation) throws Exception {
        String operation = invocation.getMethod().getAnnotation(LogVocabularyOperation.class).value();
        Map<String, Object> identifiers = identifiers(invocation);

        Uni<?> result = (Uni<?>) invocation.proceed();

        return result.withContext((uni, context) -> {
            // Nested lesson creates belong to the outer batch summary, not per-record INFO/WARN logs.
            if (context.contains(ACTIVE_OPERATION)) {
                return uni;
            }

            context.put(ACTIVE_OPERATION, true);
            long started = System.nanoTime();

            return uni.invoke(item -> completed(operation, identifiers, item, (System.nanoTime() - started) / 1_000_000))
                    .onFailure(BusinessException.class)
                    .invoke(failure -> rejected(operation, identifiers, (BusinessException) failure))
                    .onFailure().invoke(failure -> org.jboss.logmanager.MDC.put("operation", operation))
                    .onTermination().invoke(() -> {
                        context.delete(ACTIVE_OPERATION);
                        long duration = (System.nanoTime() - started) / 1_000_000;

                        if (duration >= slowOperationMs) {
                            Map<String, Object> fields = new HashMap<>(identifiers);
                            fields.put("operation", operation);
                            fields.put("durationMs", duration);
                            StructuredLog.event(LOG, Logger.Level.WARN, operation + ".slow", fields);
                        }
                    });
        });
    }

    private Map<String, Object> identifiers(InvocationContext invocation) {
        Map<String, Object> identifiers = new HashMap<>();
        Parameter[] parameters = invocation.getMethod().getParameters();
        Object[] arguments = invocation.getParameters();

        for (int i = 0; i < parameters.length; i++) {
            if (ID_PARAMETERS.contains(parameters[i].getName()) && arguments[i] instanceof Long id) {
                identifiers.put(parameters[i].getName(), id);
            }
        }
        return identifiers;
    }

    private void completed(
            String operation,
            Map<String, Object> identifiers,
            Object result,
            long duration
    ) {
        Map<String, Object> fields = new HashMap<>(identifiers);

        fields.put("operation", operation);
        fields.put("durationMs", duration);
        fields.put("outcome", "success");

        Logger.Level level = Logger.Level.INFO;

        if (result instanceof LessonBatchResponse batch) {
            fields.put("total", batch.total());
            fields.put("succeeded", batch.succeeded());
            fields.put("failed", batch.failed());

            if (batch.failed() > 0) {
                level = Logger.Level.WARN;
                fields.put("outcome", "partial_failure");
            }
        } else if (result instanceof ImportResult batch) {
            fields.put("total", batch.total());
            fields.put("created", batch.created());
            fields.put("updated", batch.updated());
        } else if (result instanceof List<?> items) {
            fields.put("count", items.size());
        } else if (result instanceof LessonResponse lesson) {
            fields.put("lessonId", lesson.id());
        }

        StructuredLog.event(LOG, level, operation + ".completed", fields);
    }

    private void rejected(String operation, Map<String, Object> identifiers, BusinessException failure) {
        String reason = failure instanceof ConflictException ? "conflict"
                : failure instanceof ResourceNotFoundException ? "not_found" : "validation";
        Logger.Level level = failure instanceof ConflictException || operation.equals("vocabulary.import")
                ? Logger.Level.WARN : Logger.Level.DEBUG;
        Map<String, Object> fields = new HashMap<>(identifiers);
        fields.put("operation", operation);
        fields.put("reason", reason);
        fields.put("outcome", "failure");
        fields.put("error.code", failure.getCode());
        fields.put("error.type", failure.getClass().getName());
        StructuredLog.event(LOG, level, operation + ".rejected", fields);
    }
}
