package com.flowops.nodepipeline.domain.notify;

import java.util.UUID;

public record DiscoveryFinding(UUID decisionId, Grain grain, String subject, double certainty, String reason) {
    public enum Grain {
        STEP_KIND,

        DRAFT_PROCESS
    }

    public String key() {
        return grain + "|" + subject;
    }
}
