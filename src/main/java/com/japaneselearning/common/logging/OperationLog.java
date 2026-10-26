package com.japaneselearning.common.logging;

import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ConflictException;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.Priority;
import jakarta.interceptor.AroundInvoke;
import jakarta.interceptor.Interceptor;
import jakarta.interceptor.InvocationContext;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

@LogOperation("")
@Interceptor
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

    // Record a domain outcome in subscription context; the interceptor logs it only after commit.
    public static <T> Uni<T> outcome(Uni<T> result, String outcome) {
        return result.withContext((uni, context) -> uni.invoke(
                item -> context.put(RESULT_EVENT, outcome)
        ));
    }

    @AroundInvoke
    Object log(InvocationContext invocation) throws Exception {
        LogOperation annotation = invocation.getMethod().getAnnotation(LogOperation.class);
        String operation = annotation.value();

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

        Object result;

        try {
            result = invocation.proceed();
        } catch (BusinessException failure) {
            rejected(operation, identifiers, failure);
            throw failure;
        }

        return ((Uni<?>) result).withContext((uni, context) -> {
            if (context.contains(ACTIVE_OPERATION)) {
                return uni;
            }

            context.put(ACTIVE_OPERATION, true);

            long started = System.nanoTime();

            StructuredLog.event(LOG, Logger.Level.DEBUG, operation + ".started", identifiers);

            return uni.invoke(item -> {
                        Map<String, Object> fields = new HashMap<>(identifiers);

                        fields.put("durationMs", (System.nanoTime() - started) / 1_000_000);
                        fields.put("outcome", "success");

                        StructuredLog.event(
                                LOG,
                                Logger.Level.INFO,
                                operation + "." + context.getOrElse(RESULT_EVENT, () -> "completed"),
                                fields
                        );
                    }).onFailure().invoke(failure -> org.jboss.logmanager.MDC.put("operation", operation))
                    .onFailure(BusinessException.class).invoke(failure -> rejected(
                                    operation, identifiers, (BusinessException) failure
                            )
                    )
                    .onTermination().invoke(() -> {
                        context.delete(ACTIVE_OPERATION);
                        context.delete(RESULT_EVENT);

                        long duration = (System.nanoTime() - started) / 1_000_000;

                        if (duration >= slowOperationMs) {
                            Map<String, Object> fields = new HashMap<>(identifiers);

                            fields.put("durationMs", duration);
                            StructuredLog.event(LOG, Logger.Level.WARN, operation + ".slow", fields);
                        }
                    });
        });
    }

    private void rejected(String operation, Map<String, Object> identifiers, BusinessException failure) {
        Map<String, Object> fields = new HashMap<>(identifiers);

        fields.put("error.type", failure.getClass().getName());
        fields.put("error.code", failure.getCode());
        fields.put("outcome", "failure");

        StructuredLog.event(
                LOG,
                failure instanceof ConflictException ? Logger.Level.WARN : Logger.Level.DEBUG,
                operation + ".rejected",
                fields
        );
    }
}
