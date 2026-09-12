package com.flowops.nodepipeline.domain.wait;

public enum WaitKind {
    CLIENT(true),

    SUPPLIER(true),

    COLLEAGUE(false),

    APPROVAL(false);

    private final boolean external;

    WaitKind(boolean external) {
        this.external = external;
    }

    public boolean external() {
        return external;
    }
}
