package com.flowops.nodepipeline.application.port;

import com.flowops.nodepipeline.domain.notify.DiscoveryFinding;
import com.flowops.nodepipeline.domain.notify.JobFinding;
import com.flowops.nodepipeline.domain.notify.NodeFinding;
import com.flowops.nodepipeline.domain.notify.NudgeHistory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PipelineFindingsPort {
    Optional<RunFindings> latestRun();

    Set<String> shownBefore(UUID runId);

    List<NudgeHistory.PriorNudge> priorNudges(UUID runId);

    void markShown(Collection<UUID> decisionIds);

    record RunFindings(
            UUID runId, List<NodeFinding> nodes, List<JobFinding> jobs, List<DiscoveryFinding> discoveries) {}
}
