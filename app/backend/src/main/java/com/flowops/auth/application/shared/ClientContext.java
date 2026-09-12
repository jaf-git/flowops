package com.flowops.auth.application.shared;

public record ClientContext(String ipAddress, String userAgent) {
    public static ClientContext unknown() {
        return new ClientContext("unknown", "unknown");
    }
}
