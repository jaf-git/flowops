package com.flowops.discovery.application.digest;

import java.util.List;
import java.util.UUID;

public interface WeeklyDigestUseCase {
    Digest execute();

    record Digest(int closedThisWeek, int proposals, int coveragePercent, List<Decision> decisions) {
        public Digest {
            decisions = List.copyOf(decisions);
        }
    }

    record Decision(Kind kind, UUID typeId, String subject, int occurrenceCount) {}

    enum Kind {
        CONFIRM_A_NAME,

        A_ROLE_STOPPED_CLICKING
    }
}
