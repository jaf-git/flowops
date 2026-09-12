package com.flowops.shared.notice;

public enum NotificationGroup {
    ASSIGNMENT(true),

    TIME(true),

    PROCESS(true),

    WEEKLY(true),

    DISCOVERY(true),

    ESCALATION(false);

    private final boolean disableable;

    NotificationGroup(boolean disableable) {
        this.disableable = disableable;
    }

    public boolean disableable() {
        return disableable;
    }
}
