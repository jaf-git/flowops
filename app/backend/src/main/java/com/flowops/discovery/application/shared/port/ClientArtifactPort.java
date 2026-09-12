package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.JobId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ClientArtifactPort {
    List<Artifact> publishedBy(JobId job, UUID caller);

    record Artifact(
            UUID artifactId,
            UUID bracketId,
            String workType,
            String kind,
            Instant publishedAt,
            String value,
            boolean readable) {}
}
