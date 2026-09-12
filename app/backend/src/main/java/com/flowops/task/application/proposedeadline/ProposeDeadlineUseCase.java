package com.flowops.task.application.proposedeadline;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface ProposeDeadlineUseCase {
    TaskTransitionResult execute(ProposeDeadlineCommand command);
}
