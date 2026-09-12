package com.flowops.task.application.approvetask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface ApproveTaskUseCase {
    TaskTransitionResult execute(ApproveTaskCommand command);
}
