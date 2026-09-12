package com.flowops.task.application.decidedeadline;

import com.flowops.task.domain.model.TaskId;

public record DecideDeadlineCommand(TaskId task, boolean accept, String reason) {}
