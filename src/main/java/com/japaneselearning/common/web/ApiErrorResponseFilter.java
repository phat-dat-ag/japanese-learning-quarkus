package com.japaneselearning.common.web;

import com.japaneselearning.common.exception.handler.HttpErrors;
import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ResponseMeta;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiErrorResponseFilter implements ContainerResponseFilter {
    private final RequestTraceContext traceContext;

    public ApiErrorResponseFilter(RequestTraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        // Framework binding, validation and security errors must not expose their diagnostic entities.
        if (response.getStatus() >= 400 && !(response.getEntity() instanceof ApiResponse<?>)) {
            response.setEntity(ApiResponse.error(HttpErrors.forStatus(response.getStatus()),
                            ResponseMeta.create(traceContext.getTraceId(), traceContext.getCorrelationId())),
                    null, MediaType.APPLICATION_JSON_TYPE);
        }
    }
}
