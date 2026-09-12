package com.flowops.task.application.starttask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface StartTaskUseCase {
    TaskTransitionResult execute(StartTaskCommand command);
}
