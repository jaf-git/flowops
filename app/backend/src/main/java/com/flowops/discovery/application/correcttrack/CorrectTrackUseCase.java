package com.flowops.discovery.application.correcttrack;

import java.util.UUID;

public interface CorrectTrackUseCase {
    void moveToLane(UUID nodeId, UUID trackId);
}
