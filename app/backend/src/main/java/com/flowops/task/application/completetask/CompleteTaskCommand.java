package com.flowops.task.application.completetask;

import com.flowops.task.domain.model.TaskId;

public record CompleteTaskCommand(TaskId task, String note, String externalLink) {}
