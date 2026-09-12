package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.PersonId;
import com.flowops.task.domain.model.TaskId;
import java.time.Instant;

public interface NotifyNegotiationPort {
    void rejected(TaskId task, PersonId assigner, String reason);

    void deadlineProposed(TaskId task, PersonId assigner, Instant proposedDeadline, String reason);

    void deadlineProposalAccepted(TaskId task, PersonId assignee, Instant newDeadline);

    void deadlineProposalDeclined(TaskId task, PersonId assignee, String reason);

    void deadlineChanged(TaskId task, PersonId assignee, Instant formerDeadline, Instant newDeadline);
}
