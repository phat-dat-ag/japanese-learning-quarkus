package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ConflictException;
import com.japaneselearning.common.exception.ErrorResponse;
import com.japaneselearning.common.exception.ResourceNotFoundException;
import com.japaneselearning.common.exception.ValidationError;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.common.web.RequestTraceContext;

import jakarta.inject.Inject;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

import java.time.Instant;
import java.util.List;

@Provider
public class BusinessExceptionMapper
        implements ExceptionMapper<BusinessException> {

    @Inject
    UriInfo uriInfo;

    @Inject
    RequestTraceContext traceContext;

    @Override
    public Response toResponse(BusinessException exception) {

        Response.Status status = resolveStatus(exception);

        List<ValidationError> errors =
                exception instanceof ValidationException validationException
                        ? validationException.getErrors()
                        : List.of();

        ErrorResponse response = new ErrorResponse(
                Instant.now(),
                status.getStatusCode(),
                exception.getCode(),
                exception.getMessage(),
                uriInfo.getRequestUri().getPath(),
                traceContext.getTraceId(),
                errors
        );

        return Response
                .status(status)
                .type(MediaType.APPLICATION_JSON)
                .entity(response)
                .build();
    }

    private Response.Status resolveStatus(
            BusinessException exception
    ) {

        if (exception instanceof ResourceNotFoundException) {
            return Response.Status.NOT_FOUND;
        }

        if (exception instanceof ConflictException) {
            return Response.Status.CONFLICT;
        }

        if (exception instanceof ValidationException) {
            return Response.Status.BAD_REQUEST;
        }

        return Response.Status.BAD_REQUEST;
    }
}