package com.flowops.task.application.reassigntask;

import com.flowops.task.application.shared.TaskTransitionResult;
import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;

public interface ReassignTaskUseCase {
    TaskTransitionResult execute(TaskId task, PersonId newAssignee, String reason);
}
