package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;

public interface NotifyTaskProgressPort {
    void blockRaised(TaskId task, PersonId assignee, String reason);

    void completionSubmitted(TaskId task, PersonId assignee);

    void approved(TaskId task, PersonId assignee);

    void returnedForRework(TaskId task, PersonId assignee, String reason);

    void reassigned(TaskId task, PersonId previousAssignee, PersonId newAssignee, String reason);

    void overridden(TaskId task, PersonId assignee, PersonId creator, String target, String reason);

    void commented(TaskId task, PersonId author, PersonId assignee, PersonId creator);
}
