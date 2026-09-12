package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.OutputType;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;

public record Fingerprint(
        UUID performerRoleId,
        Duration medianWorkPhase,
        OutputType outputType,
        UUID precedingRoleId,
        Direction precedingDirection,
        UUID followingRoleId,
        int positionInTrack) {
    public Fingerprint {
        if (positionInTrack < 1) {
            throw new IllegalArgumentException("a position inside a thread is counted from one; " + positionInTrack
                    + " is not a place anything can sit");
        }
        if ((precedingRoleId == null) != (precedingDirection == null)) {
            throw new IllegalArgumentException(
                    "a preceding role and its direction are one component; half of it is a hand-over "
                            + "whose direction nobody recorded, and a wrong direction fabricates a duration");
        }
    }

    public static Fingerprint openingATrack(
            UUID performerRoleId, Duration medianWorkPhase, OutputType outputType, UUID followingRoleId) {
        return new Fingerprint(performerRoleId, medianWorkPhase, outputType, null, null, followingRoleId, 1);
    }

    public String canonical() {
        return String.join(
                "|",
                "role=" + text(performerRoleId),
                "work=" + (medianWorkPhase == null ? "-" : Long.toString(medianWorkPhase.toSeconds())),
                "out=" + (outputType == null ? "-" : outputType.name()),
                "prev=" + text(precedingRoleId) + ":" + (precedingDirection == null ? "-" : precedingDirection.name()),
                "next=" + text(followingRoleId),
                "pos=" + positionInTrack);
    }

    public boolean matches(Fingerprint other) {
        return other != null && canonical().equals(other.canonical());
    }

    private static String text(UUID value) {
        return value == null ? "-" : value.toString();
    }

    public static Fingerprint rehydrated(
            UUID performerRoleId,
            Duration medianWorkPhase,
            OutputType outputType,
            UUID precedingRoleId,
            Direction precedingDirection,
            UUID followingRoleId,
            int positionInTrack) {
        return new Fingerprint(
                performerRoleId,
                medianWorkPhase,
                outputType,
                precedingRoleId,
                precedingDirection,
                followingRoleId,
                positionInTrack);
    }

    @Override
    public String toString() {
        return canonical();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof Fingerprint that && canonical().equals(that.canonical());
    }

    @Override
    public int hashCode() {
        return Objects.hash(canonical());
    }
}
