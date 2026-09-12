package com.flowops.workspace.api.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalTime;
import java.util.List;

public record UpdateWorkspaceSettingsRequest(
        @NotBlank @Schema(example = "Atelier Bucuresti") String name,
        @NotBlank @Schema(example = "WORK") String use,
        @NotBlank @Schema(example = "Europe/Bucharest") String timezone,
        @NotNull List<String> workingDays,
        @NotNull LocalTime workingHoursStart,
        @NotNull LocalTime workingHoursEnd,
        int atRiskWindowHours,
        @NotNull List<Integer> escalationIntervalsHours,
        @NotNull LocalTime quietHoursStart,
        @NotNull LocalTime quietHoursEnd,
        boolean invitationApprovalRequired,
        @Schema(
                        nullable = true,
                        description = "1-100. Below this closure share, an insight states what it could not"
                                + " see. Absent leaves the stored value alone.")
                Integer closureCoverageThresholdPercent,
        @Schema(
                        nullable = true,
                        description = "Positive. The idleness window used where a template shows no rhythm."
                                + " Absent leaves the stored value alone.")
                Integer templateIdleWindowDays) {}
