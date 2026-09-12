package com.flowops.task.application.closetask;

import com.flowops.task.domain.model.TaskId;

public record CloseTaskCommand(TaskId task) {}
