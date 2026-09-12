package com.flowops.process.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstanceStepResponse(
        UUID id,
        UUID definitionId,
        String title,
        String description,
        Integer expectedDurationHours,
        int position,
        String condition,
        UUID taskId,
        boolean planned,
        boolean optional,
        String conditionNote,
        boolean skipped,
        List<UUID> dependsOn,
        String taskState,
        UUID assigneeId,
        String assigneeName,
        Instant deadline,
        boolean atRisk,
        List<StepPhaseResponse> phases,
        String blockedReason) {}
