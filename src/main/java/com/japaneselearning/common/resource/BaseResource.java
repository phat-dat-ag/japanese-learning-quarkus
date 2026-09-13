package com.japaneselearning.common.resource;

import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ResponseMeta;
import com.japaneselearning.common.web.RequestTraceContext;
import jakarta.inject.Inject;
import jakarta.ws.rs.core.Response;

public abstract class BaseResource {

    @Inject
    RequestTraceContext traceContext;

    protected <T> Response success(T data) {
        ResponseMeta responseMeta = ResponseMeta.create(
                traceContext.getTraceId(),
                traceContext.getCorrelationId()
        );

        return Response.ok(
                ApiResponse.success(data, responseMeta)
        ).build();
    }
}