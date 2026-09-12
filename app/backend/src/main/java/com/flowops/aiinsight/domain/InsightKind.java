package com.flowops.aiinsight.domain;

public enum InsightKind {
    MISSING_STEP(true),

    SLOW_STEP(false),

    BLOCK_PATTERN(false),

    FALSE_DEPENDENCY(true),

    UNUSED_TEMPLATE(true),

    ESTIMATE_DIVERGENCE(true);

    private final boolean actionable;

    InsightKind(boolean actionable) {
        this.actionable = actionable;
    }

    public boolean actionable() {
        return actionable;
    }
}
