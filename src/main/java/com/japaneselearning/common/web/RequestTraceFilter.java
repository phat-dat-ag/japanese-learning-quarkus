package com.japaneselearning.common.web;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import jakarta.ws.rs.container.PreMatching;

import java.io.IOException;
import java.util.UUID;

@Provider
@PreMatching
@Priority(1000)
public class RequestTraceFilter
        implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CORRELATION_ID_HEADER = "X-Correlation-Id";

    @Inject
    RequestTraceContext traceContext;

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException {

        String traceId = getOrGenerateId(
                requestContext.getHeaderString(TRACE_ID_HEADER)
        );

        String correlationId = getOrGenerateId(
                requestContext.getHeaderString(CORRELATION_ID_HEADER)
        );

        traceContext.setTraceId(traceId);
        traceContext.setCorrelationId(correlationId);
    }

    @Override
    public void filter(
            ContainerRequestContext requestContext,
            ContainerResponseContext responseContext
    ) throws IOException {

        responseContext.getHeaders().putSingle(
                TRACE_ID_HEADER,
                traceContext.getTraceId()
        );

        responseContext.getHeaders().putSingle(
                CORRELATION_ID_HEADER,
                traceContext.getCorrelationId()
        );
    }

    private String getOrGenerateId(String id) {
        if (id == null || id.isBlank()) {
            return UUID.randomUUID()
                    .toString()
                    .replace("-", "");
        }

        return id;
    }
}