package com.japaneselearning.common.response;

import java.time.Instant;

public record ResponseMeta(
        Instant timestamp,
        String traceId,
        String correlationId
) {

    public static ResponseMeta create(String traceId, String correlationId) {
        return new ResponseMeta(Instant.now(), traceId, correlationId);
    }
}