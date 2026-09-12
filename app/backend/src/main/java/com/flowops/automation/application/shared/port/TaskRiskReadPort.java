package com.flowops.automation.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TaskRiskReadPort {
    record OverdueCandidate(UUID id, Instant deadline, boolean settled, UUID assignee, UUID assigner) {}

    record TaskInPhase(UUID id, Instant openedAt, UUID assignee, UUID assigner) {}

    List<OverdueCandidate> escalationCandidates(Instant now);

    List<TaskInPhase> blocked();

    List<TaskInPhase> awaitingReview();
}
