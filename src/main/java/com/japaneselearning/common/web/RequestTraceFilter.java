package com.japaneselearning.common.web;

import io.vertx.ext.web.RoutingContext;
import jakarta.annotation.Priority;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.container.PreMatching;
import jakarta.ws.rs.ext.Provider;

@Provider
@PreMatching
@Priority(1000)
public class RequestTraceFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String TRACE_ID_HEADER = "X-Trace-Id";
    public static final String CORRELATION_ID_HEADER = CorrelationIdFilter.HEADER;

    private final RequestTraceContext traceContext;
    private final RoutingContext routingContext;

    public RequestTraceFilter(RequestTraceContext traceContext, RoutingContext routingContext) {
        this.traceContext = traceContext;
        this.routingContext = routingContext;
    }

    @Override
    public void filter(ContainerRequestContext requestContext) {
        String legacy = CorrelationIdFilter.validatedId(
                routingContext.request().headers().getAll(TRACE_ID_HEADER)
        );

        String responseTrace = legacy != null
                ? legacy
                : routingContext.get(CorrelationIdFilter.TRACE_KEY);

        traceContext.setTraceId(responseTrace);
        routingContext.put(CorrelationIdFilter.RESPONSE_TRACE_KEY, responseTrace);
        traceContext.setCorrelationId(routingContext.get(CorrelationIdFilter.CONTEXT_KEY));
    }

    @Override
    public void filter(ContainerRequestContext requestContext, ContainerResponseContext responseContext) {
        responseContext.getHeaders().putSingle(TRACE_ID_HEADER, traceContext.getTraceId());
        responseContext.getHeaders().putSingle(CORRELATION_ID_HEADER, traceContext.getCorrelationId());
    }
}
