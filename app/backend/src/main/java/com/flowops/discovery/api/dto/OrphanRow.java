package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.assignorphan.AssignOrphanUseCase;
import java.time.Instant;
import java.util.UUID;

public record OrphanRow(
        UUID nodeId, UUID jobId, String jobName, String text, String direction, Instant markedAt, UUID saidBy) {
    public static OrphanRow of(AssignOrphanUseCase.Orphan orphan) {
        return new OrphanRow(
                orphan.nodeId(),
                orphan.jobId(),
                orphan.jobName(),
                orphan.text(),
                orphan.direction().name(),
                orphan.markedAt(),
                orphan.saidBy());
    }
}
