package com.flowops.discovery.application.blocknode;

import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.enums.WaitingOn;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.UUID;

public interface BlockNodeUseCase {
    Blocked block(BlockNode command);

    Blocked resume(ResumeNode command);

    record BlockNode(UUID nodeId, WaitingOn waitingOn) {}

    record ResumeNode(UUID nodeId) {}

    record Blocked(WorkNodeId node, PhaseKind openPhase, WaitingOn waitingOn) {}
}
