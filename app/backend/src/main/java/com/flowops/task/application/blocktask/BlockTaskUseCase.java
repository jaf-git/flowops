package com.flowops.task.application.blocktask;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface BlockTaskUseCase {
    TaskTransitionResult execute(BlockTaskCommand command);
}
