package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import java.time.Instant;
import java.util.UUID;

public interface JoinIntentPort {
    void declared(JobId job, BracketId joiner, BracketId joined, UUID by, Instant at);
}
