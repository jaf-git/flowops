package com.flowops.task.application.tasklink;

import com.flowops.task.domain.model.TaskId;
import com.flowops.task.domain.model.TaskLinkId;

public record DetachLinkCommand(TaskId task, TaskLinkId link) {}
