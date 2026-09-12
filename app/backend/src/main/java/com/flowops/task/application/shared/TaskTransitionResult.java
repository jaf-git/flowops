package com.flowops.task.application.shared;

import com.flowops.task.domain.model.Task;

public record TaskTransitionResult(Task task, String assigneeName, boolean atRisk) {}
