package com.flowops.task.application.accepttask;

import com.flowops.task.domain.model.Task;

public record AcceptTaskResult(Task task, String assigneeName, boolean atRisk) {}
