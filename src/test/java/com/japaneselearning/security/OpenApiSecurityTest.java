package com.japaneselearning.security;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.restassured.path.json.JsonPath;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@TestProfile(JwtSecurityTestProfile.class)
@QuarkusTestResource(JwksTestResource.class)
class OpenApiSecurityTest {

    @Test
    void exposesOneHttpBearerSchemeWithoutAuthentication() {
        JsonPath document = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();
        Map<String, Object> schemes = document.getMap("components.securitySchemes");
        assertEquals(Set.of("bearerAuth"), schemes.keySet());
        assertEquals("http", document.getString("components.securitySchemes.bearerAuth.type"));
        assertEquals("bearer", document.getString("components.securitySchemes.bearerAuth.scheme"));
        assertEquals("JWT", document.getString("components.securitySchemes.bearerAuth.bearerFormat"));
    }

    @Test
    void allApplicationOperationsRequireBearerAuth() {
        JsonPath document = given().accept("application/json").get("/q/openapi")
                .then().statusCode(200).extract().jsonPath();
        List<Map<String, Object>> globalSecurity = document.getList("security");
        List<Map<String, Object>> expectedSecurity = List.of(Map.of("bearerAuth", List.of()));
        assertEquals(expectedSecurity, globalSecurity);
        Map<String, Map<String, Object>> paths = document.getMap("paths");
        Map<String, String> operations = Map.of(
                "/api/v1/flashcards", "get",
                "/api/v1/flashcards/{id}", "get",
                "/api/v1/jlpt-levels", "get",
                "/api/v1/lessons", "get",
                "/api/vocabularies/import", "post"
        );
        assertEquals(operations.keySet(), paths.keySet());
        operations.forEach((path, method) -> {
            Map<?, ?> operation = (Map<?, ?>) paths.get(path).get(method);
            // OpenAPI operations inherit root security unless they explicitly override it.
            Object security = operation.containsKey("security") ? operation.get("security") : globalSecurity;
            assertEquals(expectedSecurity, security, method + " " + path);
        });
    }

    @Test
    void swaggerUiIsAccessibleWithoutAuthentication() {
        given().get("/q/swagger-ui/").then().statusCode(200)
                .body(containsString("swagger-ui-bundle.js"));
    }

    @Test
    void livenessIsAccessibleWithoutAuthentication() {
        given().get("/q/health/live").then().statusCode(200);
    }
}
