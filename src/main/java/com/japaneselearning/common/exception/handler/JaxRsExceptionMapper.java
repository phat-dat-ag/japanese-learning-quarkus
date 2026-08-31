package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.exception.ErrorResponse;
import com.japaneselearning.common.web.RequestTraceContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.List;

@Provider
public class JaxRsExceptionMapper
        implements ExceptionMapper<WebApplicationException> {

    @Inject
    UriInfo uriInfo;

    @Inject
    RequestTraceContext traceContext;

    @Override
    public Response toResponse(
            WebApplicationException exception
    ) {

        Response originalResponse =
                exception.getResponse();

        int statusCode =
                originalResponse.getStatus();

        Response.Status status =
                Response.Status.fromStatusCode(statusCode);

        String code = resolveCode(status);

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                statusCode,
                code,
                resolveMessage(exception, status),
                uriInfo.getRequestUri().getPath(),
                traceContext.getTraceId(),
                List.of()
        );

        return Response
                .status(statusCode)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }

    private String resolveCode(Response.Status status) {

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

        if (exception.getMessage() != null
                && !exception.getMessage().isBlank()) {

            return exception.getMessage();
        }

        return status.getReasonPhrase();
    }
}