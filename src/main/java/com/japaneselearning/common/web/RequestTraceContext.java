package com.japaneselearning.common.web;

import jakarta.enterprise.context.RequestScoped;

@RequestScoped
public class RequestTraceContext {

    private String traceId;

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }
}