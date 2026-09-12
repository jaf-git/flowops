package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.DeadlineProposal;
import com.flowops.task.domain.model.TaskId;
import java.util.Optional;

public interface LoadDeadlineProposalPort {
    Optional<DeadlineProposal> lockOpenProposalOf(TaskId task);

    Optional<DeadlineProposal> openProposalOf(TaskId task);

    java.util.Set<TaskId> withOpenProposalsAmong(java.util.Collection<TaskId> tasks);
}
