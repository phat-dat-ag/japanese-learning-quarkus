package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ErrorResponse;
import com.japaneselearning.common.response.ResponseMeta;
import com.japaneselearning.common.web.RequestTraceContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import org.jboss.logging.Logger;

import java.util.Objects;

@Provider
public class GlobalExceptionMapper
        implements ExceptionMapper<Exception> {

    private static final Logger LOG =
            Logger.getLogger(GlobalExceptionMapper.class);

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

        ErrorResponse errorResponse = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred"
        );

        ResponseMeta responseMeta = ResponseMeta.create(
                traceContext.getTraceId(),
                "correlationId"
        );

        ApiResponse<Objects> response = ApiResponse.error(errorResponse, responseMeta);

        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }
}