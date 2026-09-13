package com.japaneselearning.common.web;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class RequestTraceContext {

    private String traceId;
    private String correlationId;

    public String getTraceId() {
        return traceId;
    }

    public String getCorrelationId() {
        return correlationId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public void setCorrelationId(String correlationId) {
        this.correlationId = correlationId;
    }
}