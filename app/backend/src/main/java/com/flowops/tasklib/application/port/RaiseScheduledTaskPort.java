package com.flowops.tasklib.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RaiseScheduledTaskPort {
    UUID raise(ScheduledTask task);

    record ScheduledTask(
            String title,
            String description,
            UUID assigneeId,
            UUID creatorId,
            Instant deadline,
            String priority,
            UUID templateId,
            java.math.BigDecimal stampedEstimatedHours) {}
}
