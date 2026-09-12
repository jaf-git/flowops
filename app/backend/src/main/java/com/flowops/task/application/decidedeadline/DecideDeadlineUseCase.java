package com.flowops.task.application.decidedeadline;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface DecideDeadlineUseCase {
    TaskTransitionResult execute(DecideDeadlineCommand command);
}
