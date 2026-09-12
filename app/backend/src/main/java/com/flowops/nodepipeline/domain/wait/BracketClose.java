package com.flowops.nodepipeline.domain.wait;

public enum BracketClose {
    DELIVERED(true),

    DONE(true),

    DROPPED(false),

    LAPSED(false),

    PARENT_CLOSED(false),

    OVERRIDE(false),

    HANDED_OVER(false),

    CADENCE_CLOSED(false),

    MERGED(false),

    JOB_END(false),

    UNKNOWN(false);

    private final boolean completion;

    BracketClose(boolean completion) {
        this.completion = completion;
    }

    public static BracketClose read(String closeKind) {
        if (closeKind == null) {
            return null;
        }
        for (BracketClose candidate : values()) {
            if (candidate.name().equals(closeKind)) {
                return candidate;
            }
        }
        return UNKNOWN;
    }

    public boolean completes() {
        return completion;
    }
}
