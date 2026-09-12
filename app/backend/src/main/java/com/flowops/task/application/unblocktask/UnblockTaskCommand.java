package com.flowops.task.application.unblocktask;

import com.flowops.task.domain.model.TaskId;

public record UnblockTaskCommand(TaskId task, String resolution) {}
