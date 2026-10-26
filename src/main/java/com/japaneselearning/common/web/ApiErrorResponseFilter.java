package com.japaneselearning.common.web;

import com.japaneselearning.common.exception.handler.HttpErrors;
import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ResponseMeta;
import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.ext.Provider;

@Provider
public class ApiErrorResponseFilter implements ContainerResponseFilter {
    private final RequestTraceContext traceContext;
    private final RoutingContext routingContext;

    public ApiErrorResponseFilter(
            RequestTraceContext traceContext,
            RoutingContext routingContext
    ) {
        this.traceContext = traceContext;
        this.routingContext = routingContext;
    }

    @Override
    public void filter(
            ContainerRequestContext request,
            ContainerResponseContext response
    ) {
        // Framework binding, validation and security errors must not expose their diagnostic entities.
        if (response.getStatus() >= 400
                && !(response.getEntity() instanceof ApiResponse<?>)
        ) {
            response.setEntity(ApiResponse.error(
                            HttpErrors.forStatus(response.getStatus()),
                            ResponseMeta.create(
                                    traceContext.getTraceId(),
                                    traceContext.getCorrelationId()
                            )
                    ),
                    null, MediaType.APPLICATION_JSON_TYPE);
        }
        if (response.getStatus() >= 400
                && response.getEntity() instanceof ApiResponse<?> envelope
                && envelope.error() != null
        ) {
            routingContext.put(
                    CorrelationIdFilter.ERROR_CODE_KEY,
                    envelope.error().code()
            );
            routingContext.put(
                    CorrelationIdFilter.ERROR_TYPE_KEY,
                    traceContext.getErrorType()
            );
        }
    }
}
