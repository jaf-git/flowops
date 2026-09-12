package com.flowops.workspace.application.shared.port;

public interface CallerPermissionsPort {
    boolean callerHolds(String permission);
}
