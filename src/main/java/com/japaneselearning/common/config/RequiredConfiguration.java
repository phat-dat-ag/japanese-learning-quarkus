package com.japaneselearning.common.config;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.Config;

@ApplicationScoped
public class RequiredConfiguration {
    private final Config config;

    public RequiredConfiguration(Config config) {
        this.config = config;
    }

    void validateAtStartup(@Observes StartupEvent event) {
        for (String name : new String[]{"quarkus.datasource.username", "quarkus.datasource.password",
                "quarkus.datasource.reactive.url", "quarkus.oidc.auth-server-url",
                "quarkus.oidc.jwks-path", "quarkus.oidc.token.issuer", "quarkus.oidc.token.audience"}) {
            SafeConfigurationInterceptor.validate(name, config.getOptionalValue(name, String.class).orElse(null));
        }
    }
}
