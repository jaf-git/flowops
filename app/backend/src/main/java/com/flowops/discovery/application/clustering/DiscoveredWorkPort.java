package com.flowops.discovery.application.clustering;

import com.flowops.discovery.domain.model.JobId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface DiscoveredWorkPort {
    List<JobId> engagementsWithClosedWork();

    Map<UUID, String> statedJobNames();
}
