package com.flowops.task.application.starttask;

import com.flowops.task.domain.model.TaskId;

public record StartTaskCommand(TaskId task) {}
