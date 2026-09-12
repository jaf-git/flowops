package com.flowops.aiinsight.application.source;

import com.flowops.aiinsight.application.ViewInsightsUseCase.Insight;
import com.flowops.aiinsight.domain.SubjectFingerprint;
import com.flowops.aiinsight.domain.SubjectType;
import java.util.List;
import java.util.UUID;

public interface SubjectInsightReader {
    SubjectType subject();

    Computed read(UUID subjectId);

    record Computed(List<Insight> findings, SubjectFingerprint fingerprint) {
        public static Computed nothing() {
            return new Computed(List.of(), SubjectFingerprint.over(List.of()));
        }
    }
}
