package com.japaneselearning.common.web;

import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.ext.Provider;

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

        routingContext.put(
                CorrelationIdFilter.ROUTE_KEY,
                route.replaceAll("/+", "/").replaceAll("/$", "")
        );
    }
}
