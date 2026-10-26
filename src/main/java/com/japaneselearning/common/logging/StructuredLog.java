package com.japaneselearning.common.logging;

import org.jboss.logging.Logger;
import org.jboss.logmanager.MDC;

import java.util.HashMap;
import java.util.Map;

public final class StructuredLog {
    private StructuredLog() {
    }

    public static void event(Logger logger, Logger.Level level, String event, Map<String, ?> fields) {
        Map<String, String> previous = new HashMap<>();
        Map<String, Object> values = new HashMap<>(fields);

        values.put("event.name", event);

        try {
            values.forEach((key, value) -> {
                previous.put(key, MDC.get(key));
                MDC.put(key, String.valueOf(value));
            });

            logger.log(level, event);
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
