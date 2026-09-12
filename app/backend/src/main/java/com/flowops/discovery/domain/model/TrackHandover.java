package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.HandoverCause;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record TrackHandover(
        UUID id, TrackId trackId, UUID fromPerformer, UUID toPerformer, Instant at, HandoverCause cause) {
    public TrackHandover {
        Objects.requireNonNull(id, "a hand-over needs an identity");
        Objects.requireNonNull(trackId, "a hand-over is always of one thread");
        Objects.requireNonNull(fromPerformer, "a hand-over from nobody is the thread opening, not changing hands");
        Objects.requireNonNull(toPerformer, "a hand-over with nobody on the receiving end orphans the thread — I8");
        if (fromPerformer.equals(toPerformer)) {
            throw new IllegalArgumentException("a hand-over changes hands; " + fromPerformer + " handing to "
                    + "themselves would put a personnel event on a thread nothing happened to");
        }
        Objects.requireNonNull(at, "a hand-over happened at a time, and ordering is inferred from it");
        Objects.requireNonNull(cause, "a hand-over whose cause nobody recorded cannot mark its thread disrupted");
    }

    public static TrackHandover occurred(
            UUID id, TrackId trackId, UUID fromPerformer, UUID toPerformer, HandoverCause cause, Instant at) {
        return new TrackHandover(id, trackId, fromPerformer, toPerformer, at, cause);
    }

    public static TrackHandover rehydrated(
            UUID id, TrackId trackId, UUID fromPerformer, UUID toPerformer, Instant at, HandoverCause cause) {
        return new TrackHandover(id, trackId, fromPerformer, toPerformer, at, cause);
    }

    public boolean disruptsTheTrack() {
        return cause.disruptsTheTrack();
    }
}
