package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.metrics.DatabaseFailureMetrics;
import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ErrorResponse;
import com.japaneselearning.common.response.ResponseMeta;
import com.japaneselearning.common.web.RequestTraceContext;

import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Objects;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private final RequestTraceContext traceContext;
    private final DatabaseFailureMetrics databaseMetrics;

    public GlobalExceptionMapper(
            RequestTraceContext traceContext,
            DatabaseFailureMetrics databaseMetrics
    ) {
        this.traceContext = traceContext;
        this.databaseMetrics = databaseMetrics;
    }

    @Override
    public Response toResponse(Exception exception) {
        databaseMetrics.failed(exception);
        traceContext.failure(exception);

        String traceId = traceContext.getTraceId();

        SafeExceptionLog.unexpected(exception, traceId);

        ErrorResponse errorResponse = ErrorResponse.of(
                "INTERNAL_SERVER_ERROR",
                "An unexpected error occurred"
        );

        ResponseMeta responseMeta = ResponseMeta.create(
                traceContext.getTraceId(),
                traceContext.getCorrelationId()
        );

        ApiResponse<Objects> response = ApiResponse.error(errorResponse, responseMeta);

        return Response
                .status(Response.Status.INTERNAL_SERVER_ERROR)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }
}
