package com.flowops.workspace.application.manageorganisation;

import java.util.UUID;

public class FunctionalRoleIsHeldException extends RuntimeException {
    private final long held;

    public FunctionalRoleIsHeldException(UUID id, long held) {
        super("functional role " + id + " is held by " + held + " people");
        this.held = held;
    }

    public long held() {
        return held;
    }
}
