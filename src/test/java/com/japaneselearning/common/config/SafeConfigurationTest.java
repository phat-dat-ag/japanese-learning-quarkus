package com.japaneselearning.common.config;

import io.smallrye.config.SmallRyeConfigBuilder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.*;

class SafeConfigurationTest {
    @ParameterizedTest
    @CsvSource({
            "quarkus.datasource.reactive.url,mysql://user:synthetic-secret@localhost/test",
            "quarkus.datasource.reactive.url,mysql://localhost/test?password=synthetic-secret",
            "quarkus.datasource.reactive.url,synthetic-secret",
            "quarkus.oidc.auth-server-url,http://user:synthetic-secret@localhost",
            "quarkus.oidc.jwks-path,http://localhost/keys?token=synthetic-secret",
            "quarkus.oidc.jwks-path,synthetic-secret",
            "quarkus.oidc.token.issuer,''",
            "quarkus.datasource.password,' '"
    })
    void invalidValuesFailWithoutEchoingTheirContents(String name, String value) {
        var error = assertThrows(IllegalArgumentException.class,
                () -> SafeConfigurationInterceptor.validate(name, value));
        assertTrue(error.getMessage().contains(name));
        assertFalse(error.toString().contains("synthetic-secret"));
        assertNull(error.getCause());
    }

    @Test
    void interceptorValidatesAfterExpressionExpansion() {
        var config = new SmallRyeConfigBuilder().addDefaultInterceptors()
                .withInterceptors(new SafeConfigurationInterceptor())
                .withDefaultValue("AUTH_SERVER_URL", "http://user:synthetic-secret@localhost")
                .withDefaultValue("quarkus.oidc.auth-server-url", "${AUTH_SERVER_URL}").build();
        var error = assertThrows(IllegalArgumentException.class,
                () -> config.getValue("quarkus.oidc.auth-server-url", String.class));
        assertFalse(error.toString().contains("synthetic-secret"));
    }

    @Test
    void startupRequiresDatabaseCredentials() {
        var config = new SmallRyeConfigBuilder().build();
        var error = assertThrows(IllegalArgumentException.class,
                () -> new RequiredConfiguration(config).validateAtStartup(null));
        assertTrue(error.getMessage().contains("quarkus.datasource.username"));
    }

    @Test
    void validSettingsKeepLocalHttpDevelopmentAndSeparatePasswords() {
        assertDoesNotThrow(() -> {
            SafeConfigurationInterceptor.validate("quarkus.datasource.reactive.url", "mysql://mysql:3306/japanese_learning");
            SafeConfigurationInterceptor.validate("quarkus.oidc.auth-server-url", "http://localhost:5116");
            SafeConfigurationInterceptor.validate("quarkus.oidc.jwks-path", "https://auth.example.invalid/.well-known/jwks.json");
            SafeConfigurationInterceptor.validate("quarkus.datasource.password", "synthetic;quotes'\"$value");
        });
    }
}
