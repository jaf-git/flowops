package com.flowops.discovery.application.nodetrail;

import com.flowops.discovery.application.shared.port.NodeTrailPort;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.List;
import java.util.UUID;

public interface ViewNodeTrailUseCase {
    Trail execute(UUID nodeId);

    record Trail(WorkNodeId node, List<NodeTrailPort.RecordedMove> moves, boolean recordedFromTheStart) {}
}
