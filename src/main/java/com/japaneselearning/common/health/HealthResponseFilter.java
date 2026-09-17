package com.japaneselearning.common.health;

import io.smallrye.health.api.HealthContentFilter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.json.Json;
import jakarta.json.JsonObject;

@ApplicationScoped
public class HealthResponseFilter implements HealthContentFilter {

    @Override
    public JsonObject filter(JsonObject health) {
        // Built-in datasource checks can include raw database errors in data.
        var checks = Json.createArrayBuilder();
        for (var check : health.getJsonArray("checks").getValuesAs(JsonObject.class)) {
            checks.add(Json.createObjectBuilder()
                    .add("name", check.getString("name"))
                    .add("status", check.getString("status")));
        }
        return Json.createObjectBuilder()
                .add("status", health.getString("status"))
                .add("checks", checks)
                .build();
    }
}
