package com.flowops.task.application.createtask;

import java.time.Instant;
import java.util.UUID;

public interface CreateScheduledTaskUseCase {
    UUID execute(NewScheduledTask task);

    record NewScheduledTask(
            String title,
            String description,
            UUID assigneeId,
            UUID creatorId,
            Instant deadline,
            String priority,
            UUID templateId,
            java.math.BigDecimal stampedEstimatedHours) {}
}
