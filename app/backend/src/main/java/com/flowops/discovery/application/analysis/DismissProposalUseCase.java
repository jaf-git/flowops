package com.flowops.discovery.application.analysis;

import java.util.UUID;

public interface DismissProposalUseCase {
    void execute(UUID recommendationId);
}
