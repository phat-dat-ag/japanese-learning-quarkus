package com.japaneselearning.security;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jose4j.jwk.RsaJsonWebKey;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@QuarkusTest
@TestProfile(ApplicationFileLoggingTest.FileLoggingProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class ApplicationFileLoggingTest {
    RsaJsonWebKey signingKey;
    private final ObjectMapper json = new ObjectMapper();

    public static class FileLoggingProfile extends JwtSecurityTestProfile {
        static final Path LOG_FILE = Path.of("target", "logging-test", UUID.randomUUID() + ".json.log");

        @Override
        public Map<String, String> getConfigOverrides() {
            return Map.of("quarkus.log.file.enabled", "true", "quarkus.log.file.path", LOG_FILE.toString(),
                    "application.logging.slow-operation-ms", "0");
        }
    }

    @Test
    void writesTypedCorrelatedRedactedLifecycleEventsThroughAsyncFileHandler() throws Exception {
        String id = "file-" + UUID.randomUUID();
        String trace = "0123456789abcdef0123456789abcdef";
        var success = given().header("X-Correlation-ID", id).header("X-Trace-Id", "legacy-response-alias")
                .header("traceparent", "00-" + trace + "-0123456789abcdef-01")
                .get("/correlation-probe");
        assertEquals(200, success.statusCode());
        assertEquals("legacy-response-alias", success.header("X-Trace-Id"));
        assertEquals(trace, success.header("X-Distributed-Trace-Id"));
        assertEquals(id, success.asString());

        given().header("X-Correlation-ID", id + "-error").get("/error-probe/unexpected").then().statusCode(500);
        given().header("X-Correlation-ID", id + "-conflict").get("/error-probe/conflict").then().statusCode(409);
        given().header("X-Correlation-ID", id + "-validation").contentType("application/json")
                .body("{}").post("/error-probe").then().statusCode(400);
        given().header("X-Correlation-ID", id + "-json").contentType("application/json")
                .body("{credential-sentinel").post("/error-probe").then().statusCode(400);
        given().header("X-Correlation-ID", id + "-health").get("/q/health/live").then().statusCode(200);
        given().header("X-Correlation-ID", id + "-rejected").auth().oauth2("credential-sentinel")
                .get("/api/v1/jlpt-levels").then().statusCode(401);
        given().header("X-Correlation-ID", id + "-missing").get("/api/v1/jlpt-levels").then().statusCode(401);
        given().header("X-Correlation-ID", id + "-forbidden")
                .auth().oauth2(new JwtTestTokens(signingKey).token("Guest"))
                .get("/api/v1/jlpt-levels").then().statusCode(403);
        given().header("X-Correlation-ID", id + "-unknown")
                .get("/credential-sentinel?password=secret").then().statusCode(404);
        var invalid = given().header("X-Correlation-ID", "unsafe value")
                .header("traceparent", "invalid").get("/correlation-probe");
        assertTrue(invalid.header("X-Trace-Id").matches("[a-f0-9]{32}"));
        assertNotEquals("0".repeat(32), invalid.header("X-Trace-Id"));
        assertTrue(invalid.header("X-Correlation-ID").matches("[a-f0-9]{32}"));
        given().header("X-Correlation-ID", id + "-import")
                .auth().oauth2(new JwtTestTokens(signingKey).token("Admin"))
                .multiPart("file", "private-filename.json", "uploaded-secret".getBytes(), "application/json")
                .post("/api/vocabularies/import").then().statusCode(200);

        Path logFile = ConfigProvider.getConfig().getValue("quarkus.log.file.path", Path.class);
        // Await the final async write, not an assumed fixed flush delay.
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        List<JsonNode> records;
        do {
            records = readCompleteLines(logFile);
            if (events(records, id + "-import", "http.request.slow").size() == 1) {
                break;
            }
            Thread.sleep(10);
        } while (System.nanoTime() < deadline);

        for (JsonNode record : records) {
            assertTrue(record.isObject());
            Instant.parse(record.get("@timestamp").asText());
            for (String field : List.of("level", "logger.name", "thread.name", "message", "service.name",
                    "service.version", "deployment.environment")) {
                assertTrue(record.path(field).isTextual(), field);
            }
        }
        assertEquals(1, events(records, id, "http.request.started").size());
        JsonNode completed = single(records, id, "http.request.completed");
        assertEquals(trace, completed.path("traceId").asText());
        assertEquals("legacy-response-alias", completed.path("legacyTraceId").asText());
        assertEquals(200, completed.path("http.response.status_code").asInt());
        assertTrue(completed.path("http.response.status_code").isIntegralNumber());
        assertTrue(completed.path("durationMs").isIntegralNumber());
        assertEquals("/correlation-probe", completed.path("http.route").asText());
        assertEquals("success", completed.path("outcome").asText());
        for (JsonNode record : records.stream().filter(r -> id.equals(r.path("correlationId").asText())).toList()) {
            assertEquals(trace, record.path("traceId").asText());
            assertEquals(completed.path("spanId"), record.path("spanId"));
        }
        for (String suffix : List.of("-error", "-conflict", "-validation", "-json", "-rejected",
                "-missing", "-forbidden", "-unknown")) {
            JsonNode failure = single(records, id + suffix, "http.request.failed");
            assertTrue(failure.path("traceId").asText().matches("[a-f0-9]{32}"));
            assertNotEquals("0".repeat(32), failure.path("traceId").asText());
            assertTrue(failure.path("spanId").asText().matches("[a-f0-9]{16}"));
            assertFalse(failure.path("error.code").asText().isEmpty());
            assertTrue(events(records, id + suffix, "http.request.completed").isEmpty());
        }
        assertEquals("TEST_CONFLICT", single(records, id + "-conflict", "http.request.failed").path("error.code").asText());
        assertEquals("WARN", single(records, id + "-conflict", "http.request.failed").path("level").asText());
        JsonNode exception = single(records, id + "-error", "application.exception");
        assertTrue(exception.path("error.stack_trace").asText().contains("ErrorProbeResource.error"));
        assertEquals("java.lang.IllegalStateException", exception.path("error.type").asText());
        assertEquals(1, single(records, id + "-import", "vocabulary.import.completed").path("created").asInt());
        String output = records.toString();
        for (String secret : List.of("credential-sentinel", "uploaded-secret", "private-filename", "private_table",
                "password=", "unsafe value", id + "-health")) {
            assertFalse(output.contains(secret), secret);
        }
    }

    private List<JsonNode> readCompleteLines(Path path) throws Exception {
        String output = Files.readString(path);
        int end = output.lastIndexOf('\n');
        List<JsonNode> records = new ArrayList<>();
        if (end >= 0) {
            for (String line : output.substring(0, end).split("\\R")) {
                records.add(json.readTree(line));
            }
        }
        return records;
    }

    private List<JsonNode> events(List<JsonNode> records, String id, String event) {
        return records.stream().filter(r -> id.equals(r.path("correlationId").asText())
                && event.equals(r.path("event.name").asText())).toList();
    }

    private JsonNode single(List<JsonNode> records, String id, String event) {
        var matches = events(records, id, event);
        assertEquals(1, matches.size(), id + " " + event);
        return matches.get(0);
    }
}
