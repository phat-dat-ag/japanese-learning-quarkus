package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
@TestProfile(ApplicationFileLoggingTest.FileLoggingProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class ApplicationFileLoggingTest {
    RsaJsonWebKey signingKey;

    public static class FileLoggingProfile extends JwtSecurityTestProfile {
        static final Path LOG_FILE = Path.of("target", "logging-test", UUID.randomUUID() + ".log");

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.log.file.enabled", "true",
                    "quarkus.log.file.path", LOG_FILE.toString());
        }
    }

    @Test
    void writesCorrelatedSafeOutcomesThroughAsyncFileHandler() throws Exception {
        String id = "file-" + UUID.randomUUID();
        given().header("X-Correlation-ID", id).get("/correlation-probe").then().statusCode(200);
        given().header("X-Correlation-ID", id + "-error")
                .get("/error-probe/unexpected").then().statusCode(500);
        given().header("X-Correlation-ID", id + "-health")
                .get("/q/health/live").then().statusCode(200);
        given().header("X-Correlation-ID", id + "-rejected").auth().oauth2("credential-sentinel")
                .get("/api/v1/jlpt-levels").then().statusCode(401);
        given().header("X-Correlation-ID", id + "-import")
                .auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .multiPart("file", "private-filename.json", "uploaded-secret".getBytes(), "application/json")
                .post("/api/vocabularies/import").then().statusCode(200);

        Path logFile = ConfigProvider.getConfig().getValue("quarkus.log.file.path", Path.class);
        long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        String output;
        do {
            output = Files.readString(logFile);
            if (output.contains("correlationId=" + id + "-import Vocabulary import completed")) {
                break;
            }
            Thread.sleep(25);
        } while (System.nanoTime() < deadline);

        assertTrue(output.contains("correlationId=" + id + " Correlation probe application log"));
        assertTrue(output.contains("correlationId=" + id + "-error Unhandled exception"));
        assertTrue(output.contains("correlationId=" + id + "-import Vocabulary import completed total=1 created=1 updated=0"));
        assertFalse(output.contains(id + "-health"));
        assertFalse(output.contains(id + "-rejected"));
        assertFalse(output.contains("credential-sentinel"));
        assertFalse(output.contains("uploaded-secret"));
        assertFalse(output.contains("private-filename"));
        assertFalse(output.contains("private_table"));
        assertFalse(output.contains("password="));
    }
}
