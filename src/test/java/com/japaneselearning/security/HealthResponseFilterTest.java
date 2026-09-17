package com.japaneselearning.security;

import com.japaneselearning.common.health.HealthResponseFilter;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.health.SmallRyeHealthReporter;
import jakarta.inject.Inject;
import jakarta.json.Json;
import org.eclipse.microprofile.health.HealthCheck;
import org.eclipse.microprofile.health.HealthCheckResponse;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class HealthResponseFilterTest {
    @Inject
    SmallRyeHealthReporter reporter;

    @Test
    void preservesStatusesAndNamesOnly() {
        for (var status : List.of("UP", "DOWN")) {
            var input = Json.createObjectBuilder().add("status", status)
                    .add("detail", "sensitive-diagnostic")
                    .add("checks", Json.createArrayBuilder().add(Json.createObjectBuilder()
                            .add("name", "database").add("status", status)
                            .add("data", Json.createObjectBuilder().add("error", "sensitive-diagnostic"))))
                    .build();
            var output = new HealthResponseFilter().filter(input);
            assertEquals(Set.of("status", "checks"), output.keySet());
            assertEquals(status, output.getString("status"));
            var check = output.getJsonArray("checks").getJsonObject(0);
            assertEquals(Set.of("name", "status"), check.keySet());
            assertEquals("database", check.getString("name"));
            assertEquals(status, check.getString("status"));
        }
    }

    @Test
    void httpFailureResponseUsesFilterWithoutChangingFailureStatus() {
        HealthCheck failingCheck = () -> HealthCheckResponse.named("filter-regression")
                .down().withData("error", "sensitive-diagnostic").build();
        reporter.addHealthCheck(failingCheck);
        try {
            var document = given().get("/q/health").then().statusCode(503).extract().jsonPath();
            assertEquals(Set.of("status", "checks"), document.getMap("").keySet());
            assertEquals("DOWN", document.getString("status"));
            List<Map<String, Object>> checks = document.getList("checks");
            for (var check : checks) {
                assertEquals(Set.of("name", "status"), check.keySet());
            }
            assertEquals(1, checks.stream().filter(check -> "filter-regression".equals(check.get("name"))
                    && "DOWN".equals(check.get("status"))).count());
        } finally {
            reporter.removeHealthCheck(failingCheck);
        }
    }
}
