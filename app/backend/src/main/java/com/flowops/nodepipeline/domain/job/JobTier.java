package com.flowops.nodepipeline.domain.job;

public enum JobTier {
    STALE,

    IN_PROGRESS,

    NOT_SHAPE_EVIDENCE,

    REWORK,

    NOT_A_JOB,

    STANDING,

    PARTIAL_RUN,

    UNKNOWN_PATTERN,

    MIXED,

    AMBIGUOUS,

    PROCESS_RUN
}
