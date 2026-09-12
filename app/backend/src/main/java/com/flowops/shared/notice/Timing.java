package com.flowops.shared.notice;

public enum Timing {
    IMMEDIATE(false, false),

    HELD(false, true),

    ESCALATION(true, false);

    private final boolean bypassesQuietHours;
    private final boolean digestible;

    Timing(boolean bypassesQuietHours, boolean digestible) {
        this.bypassesQuietHours = bypassesQuietHours;
        this.digestible = digestible;
    }

    public boolean bypassesQuietHours() {
        return bypassesQuietHours;
    }

    public boolean digestible() {
        return digestible;
    }
}
