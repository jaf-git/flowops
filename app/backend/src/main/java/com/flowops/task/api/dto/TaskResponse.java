package com.flowops.task.api.dto;

import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.enums.TaskState;
import java.time.Instant;
import java.util.UUID;

public record TaskResponse(
        UUID id,
        String title,
        String description,
        UUID assigneeId,
        String assigneeName,
        UUID creatorId,
        Instant deadline,
        TaskPriority priority,
        TaskState state,
        boolean selfAssigned,
        boolean atRisk,
        Instant createdAt) {}
