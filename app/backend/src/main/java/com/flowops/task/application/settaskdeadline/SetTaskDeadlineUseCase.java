package com.flowops.task.application.settaskdeadline;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface SetTaskDeadlineUseCase {
    TaskTransitionResult execute(SetTaskDeadlineCommand command);
}
