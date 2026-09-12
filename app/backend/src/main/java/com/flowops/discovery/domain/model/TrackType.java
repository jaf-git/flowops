package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class TrackType {
    private final UUID id;
    private final UUID fromRoleId;
    private final UUID toRoleId;

    private String name;
    private boolean confirmedByOwner;
    private int occurrenceCount;
    private OutputType terminalOutputType;
    private TrackTypeStatus status;

    private TrackType(
            UUID id,
            String name,
            UUID fromRoleId,
            UUID toRoleId,
            boolean confirmedByOwner,
            int occurrenceCount,
            OutputType terminalOutputType,
            TrackTypeStatus status) {
        this.id = Objects.requireNonNull(id, "a type needs an identity");
        this.name = name;
        this.fromRoleId = fromRoleId;
        this.toRoleId = toRoleId;
        this.confirmedByOwner = confirmedByOwner;
        this.occurrenceCount = occurrenceCount;
        this.terminalOutputType = terminalOutputType;
        this.status = Objects.requireNonNull(status);
    }

    public static TrackType candidate(
            UUID id, UUID fromRoleId, UUID toRoleId, OutputType terminalOutputType, int occurrenceCount) {
        return new TrackType(
                id, null, fromRoleId, toRoleId, false, occurrenceCount, terminalOutputType, TrackTypeStatus.CANDIDATE);
    }

    public static TrackType rehydrated(
            UUID id,
            String name,
            UUID fromRoleId,
            UUID toRoleId,
            boolean confirmedByOwner,
            int occurrenceCount,
            OutputType terminalOutputType,
            TrackTypeStatus status) {
        return new TrackType(
                id, name, fromRoleId, toRoleId, confirmedByOwner, occurrenceCount, terminalOutputType, status);
    }

    public void instanceObserved() {
        refuseIfTerminal();
        this.occurrenceCount++;
    }

    public void instanceWithdrawn() {
        refuseIfTerminal();
        if (occurrenceCount > 0) {
            this.occurrenceCount--;
        }
    }

    public void proposed(int floor) {
        refuseIfTerminal();
        if (occurrenceCount < floor) {
            throw new IllegalStateException("type " + id + " has " + occurrenceCount + " completed threads and needs "
                    + floor + "; proposing below the floor spends one of the owner's decisions on a coincidence");
        }
        this.status = TrackTypeStatus.PROPOSED;
    }

    public void named(String confirmedName) {
        refuseIfTerminal();
        if (status != TrackTypeStatus.PROPOSED) {
            throw new IllegalStateException("type " + id + " is " + status
                    + "; machine 4.6 reaches Named from Proposed by the owner naming it");
        }
        if (confirmedName == null || confirmedName.isBlank()) {
            throw new IllegalArgumentException("a type the owner named has a name somebody can recognise it by");
        }
        this.name = confirmedName.trim();
        this.confirmedByOwner = true;
        this.status = TrackTypeStatus.NAMED;
    }

    public void rejected() {
        refuseIfTerminal();
        if (status != TrackTypeStatus.PROPOSED) {
            throw new IllegalStateException("type " + id + " is " + status + "; only a proposal can be dismissed");
        }
        this.status = TrackTypeStatus.REJECTED;
    }

    public void evidenceFellBelowFloor() {
        refuseIfTerminal();
        if (status != TrackTypeStatus.NAMED) {
            throw new IllegalStateException(
                    "type " + id + " is " + status + "; machine 4.6 draws Provisional only out of Named");
        }
        this.status = TrackTypeStatus.PROVISIONAL;
    }

    public void evidenceRecovered() {
        refuseIfTerminal();
        if (status != TrackTypeStatus.PROVISIONAL) {
            throw new IllegalStateException(
                    "type " + id + " is " + status + "; only a provisional type recovers into Named");
        }
        this.status = TrackTypeStatus.NAMED;
    }

    public void supersededBy(UUID survivingTypeId) {
        refuseIfTerminal();
        if (status != TrackTypeStatus.NAMED) {
            throw new IllegalStateException("type " + id + " is " + status
                    + "; machine 4.6 draws Superseded out of Named alone, and merging a type whose evidence has "
                    + "already lapsed would carry the lapse into the survivor unrecorded");
        }
        Objects.requireNonNull(survivingTypeId, "a superseded type says what superseded it, or it is simply gone");
        this.status = TrackTypeStatus.SUPERSEDED;
    }

    public boolean clearsTheFloor(int floor) {
        return occurrenceCount >= floor;
    }

    private void refuseIfTerminal() {
        if (status.isTerminal()) {
            throw new IllegalStateException("type " + id + " is " + status
                    + "; machine 4.6 draws nothing out of it, and a rejected shape does not return unchanged");
        }
    }

    public UUID id() {
        return id;
    }

    public Optional<String> name() {
        return Optional.ofNullable(name);
    }

    public Optional<UUID> fromRoleId() {
        return Optional.ofNullable(fromRoleId);
    }

    public Optional<UUID> toRoleId() {
        return Optional.ofNullable(toRoleId);
    }

    public boolean confirmedByOwner() {
        return confirmedByOwner;
    }

    public int occurrenceCount() {
        return occurrenceCount;
    }

    public Optional<OutputType> terminalOutputType() {
        return Optional.ofNullable(terminalOutputType);
    }

    public TrackTypeStatus status() {
        return status;
    }
}
