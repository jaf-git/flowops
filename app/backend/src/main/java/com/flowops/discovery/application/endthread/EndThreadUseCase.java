package com.flowops.discovery.application.endthread;

import com.flowops.discovery.domain.enums.CloseReason;
import com.flowops.discovery.domain.enums.Completeness;
import com.flowops.discovery.domain.model.TrackId;
import java.util.UUID;

public interface EndThreadUseCase {
    Ended execute(EndThread command);

    record EndThread(UUID trackId) {}

    record Ended(TrackId track, CloseReason closeReason, Completeness completeness) {}
}
