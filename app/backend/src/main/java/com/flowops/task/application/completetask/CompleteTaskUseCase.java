package com.flowops.task.application.completetask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface CompleteTaskUseCase {
    TaskTransitionResult execute(CompleteTaskCommand command);
}
