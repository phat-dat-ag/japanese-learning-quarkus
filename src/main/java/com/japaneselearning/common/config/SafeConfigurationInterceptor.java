package com.japaneselearning.common.config;

import io.smallrye.config.ConfigSourceInterceptor;
import io.smallrye.config.ConfigSourceInterceptorContext;
import io.smallrye.config.ConfigValue;
import io.smallrye.config.Expressions;
import io.smallrye.config.Priorities;
import jakarta.annotation.Priority;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

// Validate before framework URL conversion: its parser exceptions can echo credentials.
@Priority(Priorities.APPLICATION + 100)
public final class SafeConfigurationInterceptor implements ConfigSourceInterceptor {
    private static final Set<String> REQUIRED = Set.of(
            "quarkus.datasource.username", "quarkus.datasource.password",
            "quarkus.datasource.reactive.url", "quarkus.oidc.auth-server-url",
            "quarkus.oidc.jwks-path", "quarkus.oidc.token.issuer", "quarkus.oidc.token.audience");

    @Override
    public ConfigValue getValue(ConfigSourceInterceptorContext context, String name) {
        if (!REQUIRED.contains(name) || !Expressions.isEnabled()) {
            return context.proceed(name);
        }
        ConfigValue value;
        try {
            value = context.proceed(name);
        } catch (IllegalArgumentException exception) {
            throw invalid(name);
        }
        // Unresolved properties during augmentation are checked by runtime configuration.
        if (value != null && value.getValue() != null) {
            validate(name, value.getValue());
        }
        return value;
    }

    static void validate(String name, String value) {
        if (value == null || value.isBlank()) {
            throw invalid(name);
        }
        if (name.equals("quarkus.datasource.reactive.url")) {
            validateUrl(name, value, true);
        } else if (name.equals("quarkus.oidc.auth-server-url") || name.equals("quarkus.oidc.jwks-path")) {
            validateUrl(name, value, false);
        } else if (!name.equals("quarkus.datasource.password")
                && (value.length() > 256 || value.chars().anyMatch(Character::isISOControl))) {
            throw invalid(name);
        }
    }

    private static void validateUrl(String name, String value, boolean database) {
        try {
            URI uri = new URI(value);
            boolean schemeValid = database ? "mysql".equals(uri.getScheme())
                    : "http".equals(uri.getScheme()) || "https".equals(uri.getScheme());
            if (!schemeValid || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || uri.getPort() == 0 || uri.getPort() > 65535
                    || (database && (uri.getPath() == null || !uri.getPath().matches("/[A-Za-z0-9_-]+")))) {
                throw invalid(name);
            }
        } catch (URISyntaxException exception) {
            throw invalid(name);
        }
    }

    private static IllegalArgumentException invalid(String name) {
        return new IllegalArgumentException("Invalid required configuration: " + name
                + ". Check its documented format; credentials must be supplied separately from URLs.");
    }
}
