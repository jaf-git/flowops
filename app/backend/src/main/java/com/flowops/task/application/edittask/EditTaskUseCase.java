package com.flowops.task.application.edittask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface EditTaskUseCase {
    TaskTransitionResult execute(EditTaskCommand command);
}
