package com.flowops.aiinsight.domain;

import java.util.Objects;
import java.util.UUID;

public record InsightIdentity(InsightKind kind, SubjectType subjectType, UUID subjectId, FindingKey findingKey) {
    public InsightIdentity {
        Objects.requireNonNull(kind, "kind");
        Objects.requireNonNull(subjectType, "subjectType");
        Objects.requireNonNull(subjectId, "subjectId");
        Objects.requireNonNull(findingKey, "findingKey");
    }

    public static InsightIdentity of(InsightKind kind, SubjectType subjectType, UUID subjectId, FindingKey key) {
        return new InsightIdentity(kind, subjectType, subjectId, key);
    }
}
