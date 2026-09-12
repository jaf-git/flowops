package com.flowops.task.application.overridetask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.domain.enums.TaskState;
import com.flowops.task.domain.model.TaskId;

public interface OverrideTaskUseCase {
    TaskTransitionResult execute(TaskId task, TaskState target, String reason);
}
