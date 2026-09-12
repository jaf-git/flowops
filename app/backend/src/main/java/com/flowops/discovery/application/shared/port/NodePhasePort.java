package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.NodePhase;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

public interface NodePhasePort {
    void open(NodePhase phase);

    Optional<NodePhase> openPhaseOf(WorkNodeId node);

    void seal(NodePhase phase);

    List<NodePhase> phasesOf(WorkNodeId node);

    Optional<Duration> medianWorkPhaseOf(TrackId track);
}
