package com.flowops.discovery.application.setsubject;

import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.Optional;
import java.util.UUID;

public interface SetSubjectUseCase {
    Corrected execute(SetSubject command);

    record SetSubject(UUID nodeId, UUID jobId) {}

    record Corrected(WorkNodeId node, JobId job, Optional<TrackId> track, boolean weaklyKeyed) {}
}
