package com.flowops.process.application.shared.port;

public interface CallerPermissionsPort {
    boolean callerHolds(String permission);
}
