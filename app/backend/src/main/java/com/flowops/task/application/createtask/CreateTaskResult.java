package com.flowops.task.application.createtask;

import com.flowops.task.domain.model.Task;
import java.util.UUID;

public record CreateTaskResult(Task task, String assigneeName, boolean atRisk) {
    public UUID taskId() {
        return task.id().value();
    }
}
