package com.flowops.task.application.approvetask;

import com.flowops.task.domain.model.TaskId;

public record ApproveTaskCommand(TaskId task, Integer score, String comment) {}
