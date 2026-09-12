package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

public record WorkspaceSetupPrefillResponse(
        boolean setupCompleted,
        @Schema(example = "Europe/Bucharest") String suggestedTimezone,
        List<String> availableTimezones) {}
