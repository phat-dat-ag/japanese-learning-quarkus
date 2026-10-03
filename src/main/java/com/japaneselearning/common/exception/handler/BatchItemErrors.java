package com.japaneselearning.common.exception.handler;

import com.japaneselearning.common.exception.BusinessException;
import com.japaneselearning.common.exception.ValidationException;
import com.japaneselearning.common.response.ErrorResponse;
import com.japaneselearning.common.web.RequestTraceContext;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class BatchItemErrors {
    private final RequestTraceContext traceContext;

    public BatchItemErrors(RequestTraceContext traceContext) {
        this.traceContext = traceContext;
    }

    public ErrorResponse from(Throwable failure) {
        if (failure instanceof BusinessException business) {
            return ErrorResponse.validation(
                    business.getCode(),
                    business.getMessage(),
                    business instanceof ValidationException validation ? validation.getErrors() : List.of());
        }

        SafeExceptionLog.unexpected(failure, traceContext.getTraceId());

        return HttpErrors.forStatus(500);
    }
}
