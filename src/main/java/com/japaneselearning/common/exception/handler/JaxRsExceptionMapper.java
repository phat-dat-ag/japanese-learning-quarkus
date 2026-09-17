package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ErrorResponse;
import com.japaneselearning.common.response.ResponseMeta;
import com.japaneselearning.common.web.RequestTraceContext;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.util.Objects;

@Provider
public class JaxRsExceptionMapper
        implements ExceptionMapper<WebApplicationException> {

    private final RequestTraceContext traceContext;

    public JaxRsExceptionMapper(RequestTraceContext traceContext) {
        this.traceContext = traceContext;
    }

    @Override
    public Response toResponse(WebApplicationException exception) {

        Response originalResponse = exception.getResponse();

        int statusCode = originalResponse.getStatus();

        Response.Status status = Response.Status.fromStatusCode(statusCode);

        String code = statusCode >= 500 ? "INTERNAL_SERVER_ERROR" : resolveCode(status);

        ErrorResponse errorResponse = ErrorResponse.of(
                code,
                statusCode >= 500 ? "An unexpected error occurred" : resolveMessage(exception, status)
        );

        ResponseMeta responseMeta = ResponseMeta.create(
                traceContext.getTraceId(),
                traceContext.getCorrelationId()
        );

        ApiResponse<Objects> response = ApiResponse.error(errorResponse, responseMeta);

        return Response
                .status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }

    private String resolveCode(Response.Status status) {

        if (status == null) {
            return "HTTP_ERROR";
        }

        return switch (status) {
            case BAD_REQUEST -> "BAD_REQUEST";
            case UNAUTHORIZED -> "UNAUTHORIZED";
            case FORBIDDEN -> "FORBIDDEN";
            case NOT_FOUND -> "NOT_FOUND";
            case CONFLICT -> "CONFLICT";
            case METHOD_NOT_ALLOWED -> "METHOD_NOT_ALLOWED";
            default -> "HTTP_ERROR";
        };
    }

    private String resolveMessage(
            WebApplicationException exception,
            Response.Status status
    ) {

        if (exception.getMessage() != null && !exception.getMessage().isBlank()) {
            return exception.getMessage();
        }

        return status == null ? "HTTP request failed" : status.getReasonPhrase();
    }
}
