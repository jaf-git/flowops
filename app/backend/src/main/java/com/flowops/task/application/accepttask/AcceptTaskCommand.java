package com.flowops.task.application.accepttask;

import com.flowops.task.domain.model.TaskId;

public record AcceptTaskCommand(TaskId task) {}
