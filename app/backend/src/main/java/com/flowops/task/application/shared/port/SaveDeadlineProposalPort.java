package com.flowops.task.application.shared.port;

import com.flowops.task.domain.model.DeadlineProposal;

public interface SaveDeadlineProposalPort {
    void save(DeadlineProposal proposal);

    void update(DeadlineProposal proposal);
}
