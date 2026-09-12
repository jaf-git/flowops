package com.flowops.aiinsight.application.exception;

import com.flowops.aiinsight.domain.InsightIdentity;

public class InsightNoLongerHoldsException extends RuntimeException {
    public InsightNoLongerHoldsException(InsightIdentity identity) {
        super(identity.kind() + " no longer holds for " + identity.subjectType() + " " + identity.subjectId());
    }
}
