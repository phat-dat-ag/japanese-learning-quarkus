package com.japaneselearning.common.exception.handler;

import org.jboss.logging.Logger;

final class SafeExceptionLog {
    private static final Logger LOG = Logger.getLogger(SafeExceptionLog.class);

    private SafeExceptionLog() {
    }

    static void unexpected(Throwable exception, String traceId) {
        // Bounded code locations only; never serialize Throwable messages, data or source paths.
        StringBuilder locations = new StringBuilder();
        Throwable cause = exception;
        for (int depth = 0; cause != null && depth < 5; depth++, cause = cause.getCause()) {
            locations.append(cause.getClass().getName()).append(": ");
            StackTraceElement[] frames = cause.getStackTrace();
            for (int i = 0; i < Math.min(frames.length, 12); i++) {
                locations.append(frames[i].getClassName()).append('.').append(frames[i].getMethodName())
                        .append(':').append(frames[i].getLineNumber()).append(" <- ");
            }
        }
        LOG.errorf("Unhandled exception locations=%s traceId=%s", locations, traceId);
    }
}
