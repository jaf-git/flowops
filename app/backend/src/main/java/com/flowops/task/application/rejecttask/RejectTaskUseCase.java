package com.flowops.task.application.rejecttask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface RejectTaskUseCase {
    TaskTransitionResult execute(RejectTaskCommand command);
}
