package com.flowops.nodepipeline.domain.notify;

import com.flowops.nodepipeline.domain.MatchTier;
import java.util.UUID;

public record NodeFinding(
        UUID decisionId, String nodeId, String jobId, String templateId, MatchTier tier, double score, String reason) {
    public String key() {
        return "NODE_MATCH|" + nodeId + "|" + templateId;
    }

    public boolean speaks() {
        return tier == MatchTier.NUDGE || tier == MatchTier.ADJUST || tier == MatchTier.ROLE_MISMATCH;
    }
}
