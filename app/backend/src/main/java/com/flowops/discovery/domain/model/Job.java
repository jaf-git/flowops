package com.flowops.discovery.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class Job {
    public enum Status {
        OPEN,
        STANDING,
        DORMANT,
        CLOSED,

        READY_TO_CLOSE,

        AUTO_CLOSED,

        FORCE_CLOSED
    }

    private final JobId id;
    private final String name;
    private final UUID openedBy;
    private final Instant openedAt;

    private Status status;
    private boolean standing;
    private Instant closedAt;
    private Instant lastActivityAt;
    private UUID counterpartyId;
    private JobId reworkOfJobId;
    private String closeReason;
    private boolean shapeEligible = true;
    private String projectLabel;

    private Job(
            JobId id,
            String name,
            UUID openedBy,
            Instant openedAt,
            Status status,
            boolean standing,
            Instant closedAt,
            Instant lastActivityAt) {
        this.id = Objects.requireNonNull(id);
        this.name = requireName(name);
        this.openedBy = Objects.requireNonNull(openedBy);
        this.openedAt = Objects.requireNonNull(openedAt);
        this.status = Objects.requireNonNull(status);
        this.standing = standing;
        this.closedAt = closedAt;
        this.lastActivityAt = Objects.requireNonNull(lastActivityAt);
    }

    public static Job opened(JobId id, String name, UUID openedBy, Instant at) {
        return new Job(id, name, openedBy, at, Status.OPEN, false, null, at);
    }

    public static Job rehydrated(
            JobId id,
            String name,
            UUID openedBy,
            Instant openedAt,
            Status status,
            boolean standing,
            Instant closedAt,
            Instant lastActivityAt) {
        return new Job(id, name, openedBy, openedAt, status, standing, closedAt, lastActivityAt);
    }

    public static Job rehydrated(
            JobId id,
            String name,
            UUID openedBy,
            Instant openedAt,
            Status status,
            boolean standing,
            Instant closedAt,
            Instant lastActivityAt,
            UUID counterpartyId,
            JobId reworkOfJobId) {
        Job job = new Job(id, name, openedBy, openedAt, status, standing, closedAt, lastActivityAt);
        job.counterpartyId = counterpartyId;
        job.reworkOfJobId = reworkOfJobId;
        return job;
    }

    public void isStanding() {
        refuseIfClosed();
        this.standing = true;
        this.status = Status.STANDING;
    }

    public void touched(Instant at) {
        refuseIfClosed();
        this.lastActivityAt = at;
    }

    public void closed(Instant at) {
        refuseIfClosed();
        this.closedAt = Objects.requireNonNull(at, "an engagement that ended, ended at a time");
        this.status = Status.CLOSED;
    }

    public void dormant() {
        refuseIfClosed();
        if (status != Status.OPEN) {
            throw new IllegalStateException("job " + id.value() + " is " + status
                    + " and cannot go dormant; machine 4.3 draws that arrow out of OPEN alone");
        }
        this.status = Status.DORMANT;
    }

    public void reactivated(Instant at) {
        refuseIfClosed();
        if (status != Status.DORMANT) {
            throw new IllegalStateException("job " + id.value() + " is " + status
                    + " and has nothing to resume from; machine 4.3 reaches OPEN from DORMANT");
        }
        this.status = Status.OPEN;
        this.lastActivityAt = at;
    }

    public void readyToClose() {
        refuseIfEnded();
        this.status = Status.READY_TO_CLOSE;
    }

    public void reopened(Instant at) {
        refuseIfEnded();
        if (status != Status.READY_TO_CLOSE) {
            throw new IllegalStateException("job " + id.value() + " is " + status
                    + " and has nothing to reopen from; R15.8 draws that arrow out of READY_TO_CLOSE alone");
        }
        this.status = Status.OPEN;
        this.lastActivityAt = at;
    }

    public void autoClosed(Instant at) {
        refuseIfEnded();
        if (status != Status.READY_TO_CLOSE) {
            throw new IllegalStateException("job " + id.value() + " is " + status
                    + " and cannot close on its own; R15.8 reaches AUTO_CLOSED from READY_TO_CLOSE alone");
        }
        this.closedAt = Objects.requireNonNull(at, "an engagement that ended, ended at a time");
        this.status = Status.AUTO_CLOSED;
    }

    public void forceClosed(Instant at, String reason) {
        refuseIfEnded();
        this.closeReason = requireReason(reason);
        this.closedAt = Objects.requireNonNull(at, "an engagement that ended, ended at a time");
        this.status = Status.FORCE_CLOSED;
        this.shapeEligible = false;
    }

    public void holdsAHole() {
        this.shapeEligible = false;
    }

    public void belongsTo(UUID counterparty) {
        refuseIfClosed();
        this.counterpartyId = Objects.requireNonNull(counterparty, "an engagement belongs to somebody nameable");
    }

    public void tracks(String project) {
        refuseIfEnded();
        this.projectLabel = project == null || project.isBlank() ? null : project.trim();
    }

    public void isReworkOf(JobId original) {
        Objects.requireNonNull(original, "rework names the engagement it reworks");
        if (original.equals(id)) {
            throw new IllegalArgumentException("an engagement is not rework of itself");
        }
        if (reworkOfJobId != null) {
            throw new IllegalStateException("job " + id.value() + " is already rework of " + reworkOfJobId.value());
        }
        this.reworkOfJobId = original;
    }

    private void refuseIfClosed() {
        refuseIfEnded();
    }

    private void refuseIfEnded() {
        if (isEnded()) {
            throw new IllegalStateException("job " + id.value() + " ended as " + status
                    + "; work that returns opens a new job linked to this one");
        }
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException(
                    "R16.4 - a force close requires a reason; one without it cannot be explained later "
                            + "and cannot be told apart from a mistake");
        }
        return reason.trim();
    }

    private static String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("a job needs a name somebody can recognise it by");
        }
        return name.trim();
    }

    public JobId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public UUID openedBy() {
        return openedBy;
    }

    public Instant openedAt() {
        return openedAt;
    }

    public Status status() {
        return status;
    }

    public boolean standing() {
        return standing;
    }

    public Optional<Instant> closedAt() {
        return Optional.ofNullable(closedAt);
    }

    public Instant lastActivityAt() {
        return lastActivityAt;
    }

    public Optional<UUID> counterpartyId() {
        return Optional.ofNullable(counterpartyId);
    }

    public Optional<JobId> reworkOfJobId() {
        return Optional.ofNullable(reworkOfJobId);
    }

    public boolean isRework() {
        return reworkOfJobId != null;
    }

    public boolean isEnded() {
        return status == Status.CLOSED || status == Status.AUTO_CLOSED || status == Status.FORCE_CLOSED;
    }

    public Optional<String> closeReason() {
        return Optional.ofNullable(closeReason);
    }

    public boolean shapeEligible() {
        return shapeEligible;
    }

    public Optional<String> projectLabel() {
        return Optional.ofNullable(projectLabel);
    }

    public void restoreClosure(String closeReason, boolean shapeEligible) {
        this.closeReason = closeReason;
        this.shapeEligible = shapeEligible;
    }

    public void restoreProject(String projectLabel) {
        this.projectLabel = projectLabel;
    }
}
