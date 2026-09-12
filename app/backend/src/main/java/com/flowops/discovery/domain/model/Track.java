package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.CloseReason;
import com.flowops.discovery.domain.enums.Completeness;
import com.flowops.discovery.domain.enums.HandoverCause;
import com.flowops.discovery.domain.enums.TrackState;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Track {
    private final TrackId id;
    private final JobId job;
    private final TrackKey key;
    private final Instant openedAt;

    private TrackState state;
    private Instant closedAt;
    private Instant lastActivityAt;
    private CloseReason closeReason;
    private Completeness completeness;
    private boolean disrupted;
    private TrackId continuesTrackId;
    private UUID trackTypeId;
    private UUID processTemplateId;
    private UUID performerId;

    private Track(
            TrackId id,
            JobId job,
            TrackKey key,
            Instant openedAt,
            TrackState state,
            Instant closedAt,
            Instant lastActivityAt) {
        this.id = Objects.requireNonNull(id);
        this.job = Objects.requireNonNull(job);
        this.key = Objects.requireNonNull(key);
        this.openedAt = Objects.requireNonNull(openedAt);
        this.state = Objects.requireNonNull(state);
        this.closedAt = closedAt;
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt);
        this.performerId = key.performerId();
    }

    public static Track openedBy(TrackId id, WorkNode first, TrackKey key, Instant at) {
        if (!first.direction().canJoinATrack()) {
            throw new IllegalStateException("a " + first.direction() + " node never opens a track — invariant I11");
        }
        return new Track(id, first.job(), key, at, TrackState.OPEN, null, at);
    }

    public static Track rehydrated(
            TrackId id,
            JobId job,
            TrackKey key,
            Instant openedAt,
            TrackState state,
            Instant closedAt,
            Instant lastActivityAt) {
        return new Track(id, job, key, openedAt, state, closedAt, lastActivityAt);
    }

    public static Track rehydrated(
            TrackId id,
            JobId job,
            TrackKey key,
            Instant openedAt,
            TrackState state,
            Instant closedAt,
            Instant lastActivityAt,
            CloseReason closeReason,
            Completeness completeness,
            boolean disrupted,
            TrackId continuesTrackId,
            UUID trackTypeId,
            UUID processTemplateId,
            UUID performerId) {
        Track track = new Track(id, job, key, openedAt, state, closedAt, lastActivityAt);
        track.closeReason = closeReason;
        track.completeness = completeness;
        track.disrupted = disrupted;
        track.continuesTrackId = continuesTrackId;
        track.trackTypeId = trackTypeId;
        track.processTemplateId = processTemplateId;
        if (performerId != null) {
            track.performerId = performerId;
        }
        return track;
    }

    public void nodeAdded(Instant at) {
        refuseIfClosed();
        if (state == TrackState.OPEN) {
            state = TrackState.ACTIVE;
        }
        lastActivityAt = at;
    }

    public void dormant() {
        refuseIfClosed();
        requireState(TrackState.ACTIVE, TrackState.DORMANT);
        state = TrackState.DORMANT;
    }

    public void reactivated(Instant at) {
        refuseIfClosed();
        requireOneOf(TrackState.ACTIVE, TrackState.DORMANT, TrackState.DISRUPTED);
        state = TrackState.ACTIVE;
        lastActivityAt = at;
    }

    public void disrupted() {
        refuseIfClosed();
        requireState(TrackState.ACTIVE, TrackState.DISRUPTED);
        state = TrackState.DISRUPTED;
        disrupted = true;
    }

    public void handedOver(UUID toPerformer, HandoverCause cause, Instant at) {
        refuseIfClosed();
        Objects.requireNonNull(cause, "a handover whose cause nobody recorded cannot mark its thread disrupted");
        Objects.requireNonNull(toPerformer, "a handover with nobody on the receiving end orphans the thread — I8");
        if (cause.disruptsTheTrack()) {
            disrupted();
        } else {
            requireState(TrackState.ACTIVE, TrackState.ACTIVE);
        }
        this.performerId = toPerformer;
        this.lastActivityAt = at;
    }

    public void closed(CloseReason reason, Completeness howComplete, Instant at) {
        refuseIfClosed();
        Objects.requireNonNull(reason, "a thread that ended, ended for a reason, and the reason decides the evidence");
        Objects.requireNonNull(
                howComplete,
                "a thread closing without a completeness answer would enter discovery as whole; a half-captured "
                        + "thread does not look incomplete, it looks efficient");
        if (completeness != null) {
            throw new IllegalStateException("track " + id.value() + " already froze its completeness as " + completeness
                    + "; recomputing it after close is invariant I12's refusal, and it would walk a "
                    + "dormancy-inflated thread back into discovery");
        }
        this.closeReason = reason;
        this.completeness = howComplete;
        this.closedAt = Objects.requireNonNull(at, "a thread that closed, closed at a time");
        this.state = TrackState.CLOSED;
    }

    public void continues(TrackId predecessor) {
        Objects.requireNonNull(predecessor, "a continuation names the thread it continues");
        if (predecessor.equals(id)) {
            throw new IllegalArgumentException("a thread does not continue itself");
        }
        if (continuesTrackId != null) {
            throw new IllegalStateException("track " + id.value() + " already continues " + continuesTrackId.value()
                    + "; a second link would " + "make the chain ambiguous exactly where ordering is read from it");
        }
        this.continuesTrackId = predecessor;
    }

    public void typedAs(UUID typeId) {
        this.trackTypeId = Objects.requireNonNull(typeId, "a type match names a type");
    }

    public void composedAs(UUID processTemplate) {
        this.processTemplateId =
                Objects.requireNonNull(processTemplate, "a composition points at the template a person saved");
    }

    public boolean qualifiesForDiscovery() {
        return state.isClosed()
                && !disrupted
                && closeReason != null
                && closeReason.qualifiesForDiscovery()
                && completeness != null
                && completeness.qualifiesForDiscovery();
    }

    private void refuseIfClosed() {
        if (state.isClosed()) {
            throw new IllegalStateException(
                    "track " + id.value() + " is closed; work that returns opens a new thread linked to this one");
        }
    }

    private void requireState(TrackState from, TrackState to) {
        if (state != from) {
            throw new IllegalStateException("track " + id.value() + " is " + state + " and cannot become " + to
                    + "; machine 4.2 draws no such transition");
        }
    }

    private void requireOneOf(TrackState to, TrackState... from) {
        for (TrackState candidate : from) {
            if (state == candidate) {
                return;
            }
        }
        throw new IllegalStateException("track " + id.value() + " is " + state + " and cannot become " + to
                + "; machine 4.2 draws no such transition");
    }

    public TrackId id() {
        return id;
    }

    public JobId job() {
        return job;
    }

    public TrackKey key() {
        return key;
    }

    public TrackState state() {
        return state;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    public Optional<CloseReason> closeReason() {
        return Optional.ofNullable(closeReason);
    }

    public Optional<Completeness> completeness() {
        return Optional.ofNullable(completeness);
    }

    public boolean isDisrupted() {
        return disrupted;
    }

    public Optional<TrackId> continuesTrackId() {
        return Optional.ofNullable(continuesTrackId);
    }

    public Optional<UUID> trackTypeId() {
        return Optional.ofNullable(trackTypeId);
    }

    public Optional<UUID> processTemplateId() {
        return Optional.ofNullable(processTemplateId);
    }

    public UUID performerId() {
        return performerId;
    }

    public boolean isWeaklyKeyed() {
        return key.isWeak();
    }
}
