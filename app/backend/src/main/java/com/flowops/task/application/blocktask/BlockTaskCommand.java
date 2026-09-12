package com.flowops.task.application.blocktask;

import com.flowops.task.domain.model.TaskId;

public record BlockTaskCommand(TaskId task, String reason) {}
