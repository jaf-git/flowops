package com.flowops.discovery.domain.model;

import java.util.UUID;

public record TrackId(UUID value) {
    public TrackId {
        if (value == null) {
            throw new IllegalArgumentException("a track identifier is required");
        }
    }

    public static TrackId of(UUID value) {
        return new TrackId(value);
    }
}
