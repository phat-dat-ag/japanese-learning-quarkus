package com.japaneselearning.vocabulary.logging;

import com.japaneselearning.common.logging.StructuredLog;
import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.vocabulary.dto.LessonBatchResponse;
import com.japaneselearning.vocabulary.dto.LessonResponse;
import com.japaneselearning.vocabulary.dto.LessonWriteRequest;
import com.japaneselearning.vocabulary.importer.dto.ImportResult;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;
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
        LogVocabularyOperation annotation = invocation.getMethod().getAnnotation(LogVocabularyOperation.class);

        if (annotation == null) {
            annotation = invocation.getTarget().getClass().getAnnotation(LogVocabularyOperation.class);
        }

        String operation = annotation.value();
        String description = annotation.description();
        String successMessage = annotation.successMessage();

        Map<String, Object> identifiers = identifiers(invocation);

        Uni<?> result;
        try {
            result = (Uni<?>) invocation.proceed();
        } catch (BusinessException failure) {
            rejected(operation, description, identifiers, failure);

            throw failure;
        }

        return result.withContext((uni, context) -> {
            // Nested lesson creates belong to the outer batch summary, not per-record INFO/WARN logs.
            if (context.contains(ACTIVE_OPERATION)) {
                return uni;
            }

            context.put(ACTIVE_OPERATION, true);
            long started = System.nanoTime();

            Map<String, Object> startFields = new HashMap<>(identifiers);

            startFields.put("operation", operation);

            StructuredLog.event(
                    LOG,
                    (operation.equals("vocabulary.import") || operation.equals("lesson.batch.create"))
                            ? Logger.Level.INFO
                            : Logger.Level.DEBUG,
                    operation + ".started",
                    description + " requested",
                    startFields
            );

            return uni.invoke(item -> completed(
                            operation,
                            description,
                            successMessage,
                            identifiers,
                            item,
                            (System.nanoTime() - started) / 1_000_000
                    ))
                    .onFailure(failure -> failure instanceof BusinessException || isInputFailure(failure))
                    .invoke(failure -> rejected(operation, description, identifiers, failure))
                    .onFailure(failure -> !(failure instanceof BusinessException) && !isInputFailure(failure))
                    .invoke(failure -> {
                        Map<String, Object> fields = new HashMap<>(identifiers);

                        fields.put("operation", operation);
                        fields.put("error.type", failure.getClass().getName());
                        fields.put("durationMs", (System.nanoTime() - started) / 1_000_000);
                        fields.put("outcome", "failure");

                        StructuredLog.event(LOG, Logger.Level.DEBUG, operation + ".failed",
                                description + " failed unexpectedly; see the centralized exception diagnostic", fields);
                    })
                    .onTermination().invoke(() -> {
                        context.delete(ACTIVE_OPERATION);
                        long duration = (System.nanoTime() - started) / 1_000_000;

                        if (duration >= slowOperationMs) {
                            Map<String, Object> fields = new HashMap<>(identifiers);
                            fields.put("operation", operation);
                            fields.put("durationMs", duration);
                            StructuredLog.event(LOG, Logger.Level.WARN, operation + ".slow",
                                    description + " exceeded the configured duration threshold", fields);
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

            if (arguments[i] instanceof List<?> items) {
                identifiers.put("total", items.size());
            }

            if (arguments[i] instanceof LessonWriteRequest request && request.levelId() != null) {
                identifiers.put("levelId", request.levelId());
            }
        }

        return identifiers;
    }

    private void completed(
            String operation,
            String description,
            String successMessage,
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

        String message = successMessage.isEmpty() ? description + " completed successfully" : successMessage;

        for (String key : Set.of(
                "vocabularyId", "lessonId", "levelId", "readingId", "meaningId", "exampleId",
                "pitchAccentId", "kanjiId", "count", "total", "created", "updated", "succeeded", "failed"
        )) {
            Object value = fields.get(key);

            message = message.replace("{" + key + "}",
                    value instanceof Integer || value instanceof Long
                            ? value.toString() : "unavailable"
            );
        }

        StructuredLog.event(LOG, level, operation + ".completed", message, fields);
    }

    private boolean isInputFailure(Throwable failure) {
        return failure instanceof WebApplicationException http
                && http.getResponse().getStatus() >= 400 && http.getResponse().getStatus() < 500;
    }

    private void rejected(
            String operation,
            String description,
            Map<String, Object> identifiers,
            Throwable failure
    ) {
        String reason = failure instanceof ConflictException
                ? "conflict"
                : failure instanceof ResourceNotFoundException
                ? "not_found" : "validation";

        Map<String, Object> fields = new HashMap<>(identifiers);

        fields.put("operation", operation);
        fields.put("reason", reason);
        fields.put("outcome", "failure");

        if (failure instanceof BusinessException business) {
            fields.put("error.code", business.getCode());
        }

        fields.put("error.type", failure.getClass().getName());

        String explanation = failure instanceof ResourceNotFoundException
                ? "the requested entity or referenced assignment was not found"
                : failure instanceof ConflictException
                ? "a duplicate value, existing relationship or shared-content restriction conflicts with the request"
                : "the input or referenced assignments do not meet the operation requirements";

        if (failure instanceof BusinessException business && "JLPT_LEVEL_NOT_FOUND".equals(business.getCode())) {
            explanation = "the referenced JLPT level was not found";
        }

        StructuredLog.event(
                LOG,
                Logger.Level.WARN,
                operation + ".rejected",
                description + " rejected because " + explanation, fields
        );
    }
}
