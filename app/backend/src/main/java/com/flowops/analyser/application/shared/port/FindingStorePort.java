package com.flowops.analyser.application.shared.port;

import com.flowops.analyser.domain.Finding;
import com.flowops.analyser.domain.Lifecycle;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public interface FindingStorePort {
    Map<String, PreviousSighting> previousSightingsOf(Iterable<String> keys);

    void store(
            UUID runId,
            Finding finding,
            Presentation presentation,
            Lifecycle lifecycle,
            Instant firstSeenAt,
            int timesSeen,
            Instant now);

    record Presentation(
            String subjectName,
            com.flowops.analyser.domain.FindingContext context,
            com.flowops.analyser.domain.AnalyserStage stage) {}

    record PreviousSighting(
            int reach, Integer reachOf, Instant firstSeenAt, int timesSeen, Optional<Lifecycle> lifecycle) {}
}
