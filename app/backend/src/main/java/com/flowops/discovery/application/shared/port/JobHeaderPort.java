package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.JobId;
import java.util.Optional;
import java.util.UUID;

public interface JobHeaderPort {
    Optional<JobHeader> headerOf(JobId job);

    record JobHeader(
            UUID jobId,
            String name,
            String client,
            String project,
            String status,
            String closerName,
            int liveBrackets,
            int totalBrackets,
            String closeReason,
            boolean shapeEligible) {}
}
