package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record WorkspaceSettingsResponse(
        @Schema(nullable = true) String name,
        @Schema(nullable = true) String use,
        @Schema(example = "Europe/Bucharest") String timezone,
        @Schema(description = "Working days, Monday first.", example = "[\"MONDAY\",\"TUESDAY\"]")
                List<String> workingDays,
        LocalTime workingHoursStart,
        LocalTime workingHoursEnd,
        @Schema(description = "Hours before a deadline at which work is called at risk.") int atRiskWindowHours,
        @Schema(description = "The escalation ladder, ascending.") List<Integer> escalationIntervalsHours,
        LocalTime quietHoursStart,
        @Schema(description = "Earlier than the start when the range wraps midnight, which is normal.")
                LocalTime quietHoursEnd,
        boolean invitationApprovalRequired,
        @Schema(description = "Below this closure share, an insight states what it could not see.")
                int closureCoverageThresholdPercent,
        @Schema(description = "The idleness window used where a template shows no detectable rhythm.")
                int templateIdleWindowDays,
        Instant effectiveFrom,
        List<String> changedFields) {}
