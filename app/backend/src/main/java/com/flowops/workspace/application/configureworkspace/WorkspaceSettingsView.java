package com.flowops.workspace.application.configureworkspace;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;

public record WorkspaceSettingsView(
        String name,
        String use,
        String timezone,
        List<String> workingDays,
        LocalTime workingHoursStart,
        LocalTime workingHoursEnd,
        int atRiskWindowHours,
        List<Integer> escalationIntervalsHours,
        LocalTime quietHoursStart,
        LocalTime quietHoursEnd,
        boolean invitationApprovalRequired,
        int closureCoverageThresholdPercent,
        int templateIdleWindowDays,
        Instant effectiveFrom,
        List<String> changedFields) {}
