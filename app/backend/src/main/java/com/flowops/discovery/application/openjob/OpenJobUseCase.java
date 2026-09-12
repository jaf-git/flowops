package com.flowops.discovery.application.openjob;

import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.Optional;
import java.util.UUID;

public interface OpenJobUseCase {
    Opened execute(OpenJob command);

    record OpenJob(
            UUID messageId,
            String jobName,
            String projectLabel,
            UUID counterpartyId,
            UUID reworkOfJobId,
            boolean evenThoughOneIsOpen) {}

    record Opened(JobId job, WorkNodeId node, Optional<TrackId> track) {}
}
