package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ResponseMeta;
import com.japaneselearning.common.web.RequestTraceContext;
import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.validation.ElementKind;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

@Provider
public class EndpointValidationExceptionMapper implements ExceptionMapper<ResteasyReactiveViolationException> {
    private final RequestTraceContext traceContext;
    private final GlobalExceptionMapper unexpectedErrors;

    public EndpointValidationExceptionMapper(
            RequestTraceContext traceContext, GlobalExceptionMapper unexpectedErrors
    ) {
        this.traceContext = traceContext;
        this.unexpectedErrors = unexpectedErrors;
    }

    @Override
    public Response toResponse(ResteasyReactiveViolationException exception) {
        // Only endpoint input violations are client errors. Never expose validator paths/values/messages.
        for (var violation : exception.getConstraintViolations()) {
            for (var node : violation.getPropertyPath()) {
                if (node.getKind() == ElementKind.RETURN_VALUE) {
                    return unexpectedErrors.toResponse(exception);
                }
            }
        }

        return Response.status(400).type(MediaType.APPLICATION_JSON)
                .entity(ApiResponse.error(HttpErrors.forStatus(400),
                        ResponseMeta.create(traceContext.getTraceId(), traceContext.getCorrelationId())))
                .build();
    }
}
