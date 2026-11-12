package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.logging.StructuredLog;

import org.jboss.logging.Logger;

import java.util.HashMap;

final class SafeExceptionLog {
    private static final Logger LOG = Logger.getLogger(SafeExceptionLog.class);

    private SafeExceptionLog() {
    }

    static void unexpected(Throwable exception, String traceId) {
        // Bounded code locations only; never serialize Throwable messages, data or source paths.
        StringBuilder locations = new StringBuilder();
        Throwable cause = exception;

        int remainingFrames = 20;

        for (int depth = 0; cause != null && depth < 3; depth++, cause = cause.getCause()) {
            locations.append(cause.getClass().getName()).append(": ");
            StackTraceElement[] frames = cause.getStackTrace();

            int frameLimit = Math.min(frames.length, 12);

            for (int i = 0; i < frameLimit && remainingFrames > 0; i++, remainingFrames--) {
                locations
                        .append(frames[i].getClassName())
                        .append('.')
                        .append(frames[i].getMethodName())
                        .append(':')
                        .append(frames[i].getLineNumber())
                        .append(" <- ");
            }
        }

        if (cause != null || remainingFrames == 0) {
            locations.append("[truncated]");
        }

        var fields = new HashMap<String, Object>();

        fields.put("error.type", exception.getClass().getName());
        fields.put("error.code", "INTERNAL_SERVER_ERROR");
        fields.put("error.stack_trace", locations.toString());

        StructuredLog.event(
                LOG,
                Logger.Level.ERROR,
                "application.exception",
                "Request failed because of an unexpected internal error",
                fields
        );
    }
}
