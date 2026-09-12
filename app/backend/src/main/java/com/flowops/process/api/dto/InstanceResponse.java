package com.flowops.process.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record InstanceResponse(
        UUID id,
        String name,
        String state,
        UUID templateId,
        String templateName,
        UUID processOwnerId,
        Instant startedAt,
        Instant completedAt,
        ProgressResponse progress,
        List<InstanceStepResponse> steps,
        List<InstanceEdgeResponse> edges,
        List<UUID> awaitingAssignment,
        BottleneckResponse bottleneck,
        Long totalDurationMinutes,
        Instant abandonedAt,
        String abandonedReason,
        String closureNote,
        List<UUID> needingAttention) {}
