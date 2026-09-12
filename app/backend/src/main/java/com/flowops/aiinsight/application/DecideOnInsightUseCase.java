package com.flowops.aiinsight.application;

import com.flowops.aiinsight.domain.InsightIdentity;

public interface DecideOnInsightUseCase {
    void apply(InsightIdentity identity);

    void dismiss(InsightIdentity identity);
}
