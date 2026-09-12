package com.flowops.task.application.shared.port;

public interface CallerPermissionsPort {
    boolean callerHolds(String permission);
}
