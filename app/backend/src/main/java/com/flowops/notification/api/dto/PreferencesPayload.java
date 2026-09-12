package com.flowops.notification.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.Map;

@Schema(description = "What interrupts you. Four groups; escalation is absent because it has no switch.")
public record PreferencesPayload(boolean assignment, boolean time, boolean process, boolean weekly) {
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void refuseAnythingElse(String name, Object value) {
        throw new com.flowops.notification.application.shared.exception.NotADisableableGroupException(name);
    }

    public static PreferencesPayload of(Map<String, Boolean> groups) {
        return new PreferencesPayload(
                groups.getOrDefault("ASSIGNMENT", true),
                groups.getOrDefault("TIME", true),
                groups.getOrDefault("PROCESS", true),
                groups.getOrDefault("WEEKLY", true));
    }

    public Map<String, Boolean> asGroups() {
        return Map.of("ASSIGNMENT", assignment, "TIME", time, "PROCESS", process, "WEEKLY", weekly);
    }
}
