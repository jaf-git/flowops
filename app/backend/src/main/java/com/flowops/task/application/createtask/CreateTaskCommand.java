package com.flowops.task.application.createtask;

import java.time.Instant;
import java.util.UUID;

public record CreateTaskCommand(
        String title,
        String description,
        UUID assignee,
        Instant deadline,
        String priority,
        UUID templateId,
        java.math.BigDecimal stampedEstimatedHours,
        String kind) {}
