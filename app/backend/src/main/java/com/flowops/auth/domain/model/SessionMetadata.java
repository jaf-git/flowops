package com.flowops.auth.domain.model;

import java.util.Objects;

public record SessionMetadata(String ipAddress, String deviceSummary, String coarseLocation) {
    public static final String UNKNOWN = "unknown";

    public SessionMetadata {
        Objects.requireNonNull(ipAddress, "an address is required; use unknown");
        Objects.requireNonNull(deviceSummary, "a device summary is required; use unknown");
        Objects.requireNonNull(coarseLocation, "a location is required; use unknown");
    }

    public static SessionMetadata unknown() {
        return new SessionMetadata(UNKNOWN, UNKNOWN, UNKNOWN);
    }
}
