package com.flowops.automation.application.shared.port;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface StepRiskReadPort {
    record StalledCandidate(UUID id, Instant reachableAt, UUID runOwner) {}

    List<StalledCandidate> reachableAndUnassigned();
}
