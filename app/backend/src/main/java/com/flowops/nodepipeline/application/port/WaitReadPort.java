package com.flowops.nodepipeline.application.port;

import com.flowops.nodepipeline.domain.wait.WaitSpan;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WaitReadPort {
    Optional<JobWindow> windowOf(UUID jobId);

    List<WaitSpan> waitsIn(UUID jobId);

    record JobWindow(UUID jobId, Instant openedAt, Instant closedAt) {}
}
