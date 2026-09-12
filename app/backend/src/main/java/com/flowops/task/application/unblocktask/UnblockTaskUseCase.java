package com.flowops.task.application.unblocktask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface UnblockTaskUseCase {
    TaskTransitionResult execute(UnblockTaskCommand command);
}
