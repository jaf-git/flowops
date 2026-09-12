package com.flowops.tasklib.application.port;

import java.time.Instant;
import java.util.UUID;

public interface CreateTaskFromTemplatePort {
    UUID createFromTemplate(NewTask task);

    record NewTask(
            String title,
            String description,
            UUID assigneeId,
            Instant deadline,
            String priority,
            UUID templateId,
            java.math.BigDecimal stampedEstimatedHours) {}
}
