package com.japaneselearning.common.logging;

import org.jboss.logging.Logger;
import org.jboss.logmanager.MDC;

import java.util.HashMap;
import java.util.Map;

public final class StructuredLog {
    private StructuredLog() {
    }

    public static void event(
            Logger logger,
            Logger.Level level,
            String event,
            Map<String, ?> fields
    ) {
        event(logger, level, event, event, fields);
    }

    // Messages must contain only static text and explicitly safe scalar values.
    public static void event(
            Logger logger,
            Logger.Level level,
            String event,
            String message,
            Map<String, ?> fields
    ) {
        if (!logger.isEnabled(level)) {
            return;
        }

        Map<String, String> previous = new HashMap<>();
        Map<String, Object> values = new HashMap<>(fields);

        values.put("event.name", event);

        try {
            values.forEach((key, value) -> {
                previous.put(key, MDC.get(key));
                MDC.put(key, String.valueOf(value));
            });

            logger.log(level, message);
        } finally {
            previous.forEach((key, value) -> {
                if (value == null) {
                    MDC.remove(key);
                } else {
                    MDC.put(key, value);
                }
            });
        }
    }
}
