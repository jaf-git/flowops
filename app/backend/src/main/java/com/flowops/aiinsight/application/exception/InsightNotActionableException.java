package com.flowops.aiinsight.application.exception;

import com.flowops.aiinsight.domain.InsightKind;

public class InsightNotActionableException extends RuntimeException {
    public InsightNotActionableException(InsightKind kind) {
        super(kind + " is informational and proposes no change");
    }
}
