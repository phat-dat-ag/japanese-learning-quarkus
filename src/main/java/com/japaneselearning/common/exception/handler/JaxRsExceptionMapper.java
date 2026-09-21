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

        ErrorResponse errorResponse = HttpErrors.forStatus(statusCode);
        if (statusCode >= 500) {
            SafeExceptionLog.unexpected(exception, traceContext.getTraceId());
        }

        ResponseMeta responseMeta = ResponseMeta.create(
                traceContext.getTraceId(),
                traceContext.getCorrelationId()
        );

        ApiResponse<Objects> response = ApiResponse.error(errorResponse, responseMeta);

        Response.ResponseBuilder builder = Response.status(statusCode)
                .type(MediaType.APPLICATION_JSON).entity(response);
        // Preserve protocol semantics without copying arbitrary exception headers or entities.
        for (String name : new String[]{"Allow", "WWW-Authenticate", "Retry-After"}) {
            if (originalResponse.getHeaders().containsKey(name)) {
                originalResponse.getHeaders().get(name).forEach(value -> builder.header(name, value));
            }
        }
        return builder.build();
    }
}
