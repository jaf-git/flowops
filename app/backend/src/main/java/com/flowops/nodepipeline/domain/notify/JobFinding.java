package com.flowops.nodepipeline.domain.notify;

import com.flowops.nodepipeline.domain.job.JobTier;
import java.util.UUID;

public record JobFinding(UUID decisionId, String jobId, JobTier tier, String processId, double score, String reason) {
    public String key() {
        return "JOB_MATCH|" + jobId + "|" + tier;
    }
}
