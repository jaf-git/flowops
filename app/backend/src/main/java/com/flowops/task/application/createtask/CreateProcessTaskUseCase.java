package com.flowops.task.application.createtask;

import java.time.Instant;
import java.util.UUID;

public interface CreateProcessTaskUseCase {
    UUID execute(NewProcessTask task);

    record NewProcessTask(
            String title,
            String description,
            UUID assigneeId,
            UUID creatorId,
            Instant deadline,
            String priority,
            UUID processInstanceId,
            UUID instanceStepId,
            UUID taskTemplateId) {}
}
