package com.japaneselearning.common.logging;

import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@LogOperation("")
@Interceptor
// Observe the WithTransaction interceptor's completed Uni, including commit failures.
@Priority(Interceptor.Priority.PLATFORM_BEFORE + 199)
public class OperationLog {
    private static final Logger LOG = Logger.getLogger(OperationLog.class);
    private static final Object ACTIVE_OPERATION = new Object();
    private static final Object RESULT_EVENT = new Object();

    private static final Set<String> IDS = Set.of(
            "sessionId", "itemId", "vocabularyId", "lessonId", "levelId", "questionId"
    );

    @ConfigProperty(name = "application.logging.slow-operation-ms", defaultValue = "1000")
    long slowOperationMs = 1000;

    private record ResultEvent(String name, String message, Map<String, ?> fields) {
    }

    // Subscription-local metadata is emitted only after the enclosing transaction completes.
    public static <T> Uni<T> outcome(Uni<T> result, String outcome) {
        return outcome(result, outcome, Map.of());
    }

    public static <T> Uni<T> outcome(Uni<T> result, String outcome, Map<String, ?> fields) {
        return outcome(result, outcome, "", fields);
    }

    public static <T> Uni<T> outcome(Uni<T> result, String outcome, String message, Map<String, ?> fields) {
        ResultEvent event = new ResultEvent(outcome, message, Map.copyOf(fields));

        return result.withContext((uni, context) -> uni.invoke(item -> context.put(RESULT_EVENT, event)));
    }

    @AroundInvoke
    Object log(InvocationContext invocation) throws Exception {
        LogOperation annotation = invocation.getMethod().getAnnotation(LogOperation.class);
        if (annotation == null) {
            annotation = invocation.getTarget().getClass().getAnnotation(LogOperation.class);
        }

        String operation = annotation.value();
        String description = annotation.description();
        String successMessage = annotation.successMessage();
        Logger.Level completionLevel = annotation.level();

        Map<String, Object> identifiers = new HashMap<>();

        identifiers.put("operation", operation);

        var parameters = invocation.getMethod().getParameters();
        for (int i = 0; i < parameters.length; i++) {
            String name = parameters[i].getName();

            if (name.equals("id")) {
                name = annotation.id();
            }

            if (IDS.contains(name) && invocation.getParameters()[i] instanceof Long id) {
                identifiers.put(name, id);
            }
        }

        long invoked = System.nanoTime();
        boolean reactive = Uni.class.isAssignableFrom(invocation.getMethod().getReturnType());

        if (!reactive) {
            StructuredLog.event(
                    LOG,
                    Logger.Level.DEBUG,
                    operation + ".started",
                    description.isEmpty()
                            ? operation + ".started"
                            : description + " requested",
                    identifiers
            );
        }

        Object result;
        try {
            result = invocation.proceed();
        } catch (Exception failure) {
            failed(operation, description, identifiers, failure, invoked);
            slow(operation, description, identifiers, invoked);

            throw failure;
        }

        if (!(result instanceof Uni<?> uniResult)) {
            completed(
                    operation,
                    description,
                    successMessage,
                    identifiers,
                    invoked,
                    completionLevel,
                    new ResultEvent("completed", "", Map.of())
            );

            slow(operation, description, identifiers, invoked);

            return result;
        }

        return uniResult.withContext((uni, context) -> {
            if (context.contains(ACTIVE_OPERATION)) {
                return uni;
            }

            context.put(ACTIVE_OPERATION, true);

            long started = System.nanoTime();
            StructuredLog.event(
                    LOG,
                    Logger.Level.DEBUG,
                    operation + ".started",
                    description.isEmpty()
                            ? operation + ".started"
                            : description + " requested",
                    identifiers
            );

            return uni.invoke(item -> completed(
                            operation,
                            description,
                            successMessage,
                            identifiers,
                            started,
                            completionLevel,
                            context.getOrElse(
                                    RESULT_EVENT,
                                    () -> new ResultEvent("completed", "", Map.of())
                            )
                    ))
                    .onFailure().invoke(failure -> failed(
                            operation,
                            description,
                            identifiers,
                            failure,
                            started
                    ))
                    .onCancellation().invoke(() -> StructuredLog.event(
                            LOG,
                            Logger.Level.DEBUG,
                            operation + ".cancelled",
                            description.isEmpty()
                                    ? operation + ".cancelled"
                                    : description + " was cancelled before completion",
                            timed(identifiers, started)
                    ))
                    .onTermination().invoke(() -> {
                        context.delete(ACTIVE_OPERATION);
                        context.delete(RESULT_EVENT);
                        slow(operation, description, identifiers, started);
                    });
        });
    }

    private void completed(
            String operation,
            String description,
            String successMessage,
            Map<String, Object> identifiers,
            long started,
            Logger.Level level,
            ResultEvent event
    ) {
        Map<String, Object> fields = timed(identifiers, started);
        fields.putAll(event.fields());

        boolean partial = event.name().equals("partial_failure");

        fields.put("outcome", partial ? "partial_failure" : "success");
        String message = event.message().isEmpty() ? successMessage : event.message();

        if (message.isEmpty()) {
            message = description.isEmpty()
                    ? operation + "." + event.name()
                    : description + (partial ? " completed with failed items" : " completed successfully");
        }

        StructuredLog.event(
                LOG,
                partial
                        ? Logger.Level.WARN
                        : level, operation + "." + event.name(),
                numericMessage(message, fields),
                fields
        );
    }

    private void failed(
            String operation,
            String description,
            Map<String, Object> identifiers,
            Throwable failure,
            long started
    ) {
        Map<String, Object> fields = timed(identifiers, started);

        fields.put("error.type", failure.getClass().getName());
        fields.put("outcome", "failure");

        if (failure instanceof BusinessException business) {
            fields.put("error.code", business.getCode());
        }
        // Unexpected stacks belong to the central mapper, not every intercepted layer.
        boolean inputFailure = !description.isEmpty()
                && failure instanceof WebApplicationException http
                && http.getResponse().getStatus() >= 400
                && http.getResponse().getStatus() < 500;

        String event = operation + (failure instanceof BusinessException || inputFailure ? ".rejected" : ".failed");
        String message = event;

        if (!description.isEmpty()) {
            message = description + (failure instanceof ResourceNotFoundException
                    ? " rejected because the requested resource was not found or is unavailable"
                    : inputFailure
                    ? " rejected because the uploaded input does not meet the request requirements"
                    : failure instanceof ConflictException
                    ? " rejected because the current state conflicts with the request or a concurrent modification occurred"
                    : failure instanceof BusinessException
                    ? " rejected because an input or business validation requirement was not satisfied"
                    : " failed unexpectedly; see the centralized exception diagnostic");
        }

        StructuredLog.event(
                LOG,
                failure instanceof ConflictException
                        || inputFailure
                        || (!description.isEmpty() && failure instanceof BusinessException)
                        ? Logger.Level.WARN
                        : Logger.Level.DEBUG,
                event,
                message,
                fields
        );
    }

    private void slow(
            String operation,
            String description,
            Map<String, Object> identifiers,
            long started
    ) {
        Map<String, Object> fields = timed(identifiers, started);

        if ((long) fields.get("durationMs") >= slowOperationMs) {
            StructuredLog.event(
                    LOG,
                    Logger.Level.WARN,
                    operation + ".slow",
                    description.isEmpty()
                            ? operation + ".slow"
                            : description + " exceeded the configured duration threshold",
                    fields
            );
        }
    }

    private String numericMessage(String template, Map<String, Object> fields) {
        String message = template;

        for (String key : Set.of(
                "sessionId", "itemId", "questionId", "count", "total", "created",
                "updated", "succeeded", "failed", "page", "size"
        )) {
            Object value = fields.get(key);

            String replacement = value instanceof Integer || value instanceof Long
                    ? value.toString()
                    : "unavailable";

            message = message.replace("{" + key + "}", replacement);
        }

        return message;
    }

    private Map<String, Object> timed(Map<String, Object> identifiers, long started) {
        Map<String, Object> fields = new HashMap<>(identifiers);

        fields.put("durationMs", (System.nanoTime() - started) / 1_000_000);

        return fields;
    }
}
