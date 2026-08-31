package com.japaneselearning.common.web;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;

import java.io.IOException;
import java.util.UUID;

@Provider
@Priority(1000)
public class RequestTraceFilter
        implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    @Inject
    RequestTraceContext traceContext;

    @Override
    public void filter(ContainerRequestContext requestContext)
            throws IOException {

        String traceId =
                requestContext.getHeaderString(TRACE_ID_HEADER);

        if (traceId == null || traceId.isBlank()) {
            traceId = UUID.randomUUID()
                    .toString()
                    .replace("-", "");
        }

        traceContext.setTraceId(traceId);
    }

    @Override
    public void filter(
            ContainerRequestContext requestContext,
            ContainerResponseContext responseContext
    ) throws IOException {

        String traceId = traceContext.getTraceId();

        if (traceId != null) {
            responseContext.getHeaders().putSingle(
                    TRACE_ID_HEADER,
                    traceId
            );
        }
    }
}