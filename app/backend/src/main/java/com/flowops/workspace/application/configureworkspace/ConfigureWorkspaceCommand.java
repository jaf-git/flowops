package com.flowops.workspace.application.configureworkspace;

import java.time.LocalTime;
import java.util.List;

public record ConfigureWorkspaceCommand(
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
        Integer closureCoverageThresholdPercent,
        Integer templateIdleWindowDays) {}
