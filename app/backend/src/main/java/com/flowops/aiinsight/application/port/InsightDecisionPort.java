package com.flowops.aiinsight.application.port;

import com.flowops.aiinsight.domain.DecisionOutcome;
import com.flowops.aiinsight.domain.InsightIdentity;
import com.flowops.aiinsight.domain.RecordedDecision;
import com.flowops.aiinsight.domain.SubjectFingerprint;
import com.flowops.aiinsight.domain.SubjectType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface InsightDecisionPort {
    void record(
            InsightIdentity identity,
            DecisionOutcome outcome,
            SubjectFingerprint fingerprint,
            UUID deciderUserId,
            Instant decidedAt);

    List<RecordedDecision> decisionsFor(SubjectType subjectType, UUID subjectId);
}
