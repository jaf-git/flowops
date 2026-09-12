package com.flowops.task.application.returntaskforrework;

import com.flowops.task.application.shared.TaskTransitionResult;

public interface ReturnTaskForReworkUseCase {
    TaskTransitionResult execute(ReturnTaskForReworkCommand command);
}
