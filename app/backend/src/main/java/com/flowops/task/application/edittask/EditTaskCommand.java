package com.flowops.task.application.edittask;

import com.flowops.task.domain.enums.TaskPriority;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;

public record EditTaskCommand(TaskId task, Instant deadline, TaskPriority priority, String description) {}
