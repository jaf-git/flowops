package com.flowops.task.application.rejecttask;

import com.flowops.task.domain.model.TaskId;

public record RejectTaskCommand(TaskId task, String reason) {}
