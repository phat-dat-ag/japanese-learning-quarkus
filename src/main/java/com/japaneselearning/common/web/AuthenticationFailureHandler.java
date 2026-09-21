package com.japaneselearning.common.web;

import com.japaneselearning.common.exception.handler.HttpErrors;
import com.japaneselearning.common.response.ApiResponse;
import com.japaneselearning.common.response.ResponseMeta;
import io.quarkus.security.AuthenticationFailedException;
import io.vertx.ext.web.Router;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;

@ApplicationScoped
public class AuthenticationFailureHandler {
    void register(@Observes Router router) {
        router.route().failureHandler(context -> {
            // Proactive authentication runs before REST. Retain its status and challenge headers.
            if (context.failure() instanceof AuthenticationFailedException && !context.response().ended()
                    && context.response().getStatusCode() == 401) {
                String id = context.get(CorrelationIdFilter.CONTEXT_KEY);
                context.json(ApiResponse.error(HttpErrors.forStatus(401), ResponseMeta.create(id, id)));
            } else {
                context.next();
            }
        });
    }
}
