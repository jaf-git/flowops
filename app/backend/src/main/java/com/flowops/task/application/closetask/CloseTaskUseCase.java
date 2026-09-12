package com.flowops.task.application.closetask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface CloseTaskUseCase {
    TaskTransitionResult execute(CloseTaskCommand command);
}
