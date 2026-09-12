package com.flowops.notification.domain;

public enum NotificationState {
    QUEUED(true),

    HELD(true),

    DELIVERED(true),

    READ(false),

    CANCELLED(false);

    private final boolean pending;

    NotificationState(boolean pending) {
        this.pending = pending;
    }

    public boolean pending() {
        return pending;
    }
}
