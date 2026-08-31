package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.exception.ErrorResponse;
import com.japaneselearning.common.web.RequestTraceContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;

@Provider
public class GlobalExceptionMapper
        implements ExceptionMapper<Exception> {

    private static final Logger LOG =
            Logger.getLogger(GlobalExceptionMapper.class);

    @Inject
    UriInfo uriInfo;

    @Inject
    RequestTraceContext traceContext;

    @Override
    public Response toResponse(Exception exception) {

        String traceId =
                traceContext.getTraceId();

        LOG.errorf(
                exception,
                "Unhandled exception. traceId=%s",
                traceId
        );

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                Response.Status.INTERNAL_SERVER_ERROR.getStatusCode(),
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred",
                uriInfo.getRequestUri().getPath(),
                traceId,
                List.of()
        );

        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }
}