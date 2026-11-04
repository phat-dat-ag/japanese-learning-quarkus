package com.japaneselearning.common.web;

import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;
import org.jboss.logmanager.MDC;

import java.util.Map;

@Provider
public class RequestRouteFilter implements ContainerRequestFilter {
    @Context
    ResourceInfo resourceInfo;
    private final RoutingContext routingContext;

    public RequestRouteFilter(RoutingContext routingContext) {
        this.routingContext = routingContext;
    }

    @Override
    public void filter(ContainerRequestContext request) {
        Path root = resourceInfo.getResourceClass().getAnnotation(Path.class);
        Path method = resourceInfo.getResourceMethod().getAnnotation(Path.class);

        String route = (root == null ? "" : root.value())
                + "/" + (method == null ? "" : method.value());

        route = route.replaceAll("/+", "/").replaceAll("/$", "");
        routingContext.put(CorrelationIdFilter.ROUTE_KEY, route);

        Map<String, Object> fields = routingContext.get(CorrelationIdFilter.FIELDS_KEY);

        if (fields != null) {
            fields.put("http.route", route);
            fields.put("handler.class", resourceInfo.getResourceClass().getName());
            fields.put("handler.method", resourceInfo.getResourceMethod().getName());
            fields.put("security.authenticated", request.getSecurityContext().getUserPrincipal() != null);
            fields.forEach((key, value) -> MDC.put(key, String.valueOf(value)));
        }
    }
}
