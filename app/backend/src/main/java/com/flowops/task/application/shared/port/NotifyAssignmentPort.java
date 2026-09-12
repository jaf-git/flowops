package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;

public interface NotifyAssignmentPort {
    void assignmentGiven(TaskId task, PersonId assignee);
}
