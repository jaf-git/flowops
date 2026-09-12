package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.BracketState;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.OutputKind;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class WorkBracket {
    private final BracketId id;
    private final JobId jobId;
    private final WorkNodeId openedByNode;
    private final Instant openedAt;
    private final boolean boundary;
    private final int depth;
    private final BracketId parentBracketId;
    private final BracketId continuesBracketId;

    private BracketAddress address;

    private BracketState state;
    private UUID closureRight;

    private UUID closureStandsInFor;

    private WorkNodeId closedByNode;
    private CloseKind closeKind;
    private OutputKind outputKind;
    private String outputValue;
    private boolean disrupted;

    private boolean workTypeOverridden;

    private Instant nudgedAt;
    private boolean answeredNudge;
    private Instant closedAt;
    private Instant lastActivityAt;

    private WorkBracket(
            BracketId id,
            JobId jobId,
            BracketAddress address,
            WorkNodeId openedByNode,
            UUID closureRight,
            Instant openedAt,
            boolean boundary,
            int depth,
            BracketId parentBracketId,
            BracketId continuesBracketId) {
        this.id = Objects.requireNonNull(id);
        this.jobId = Objects.requireNonNull(jobId);
        this.address = Objects.requireNonNull(address);
        this.openedByNode = Objects.requireNonNull(openedByNode);
        this.closureRight =
                Objects.requireNonNull(closureRight, "R5.1 - a bracket opens with somebody able to close it");
        this.openedAt = Objects.requireNonNull(openedAt);
        this.boundary = boundary;
        this.depth = requireDepth(depth);
        this.parentBracketId = parentBracketId;
        this.continuesBracketId = continuesBracketId;
        this.state = BracketState.OPEN;
        this.lastActivityAt = openedAt;
    }

    public static WorkBracket opened(
            BracketId id, JobId jobId, BracketAddress address, WorkNodeId startNode, UUID marker, Instant at) {
        return new WorkBracket(id, jobId, address, startNode, marker, at, false, 0, null, null);
    }

    public static WorkBracket boundary(
            BracketId id, JobId jobId, BracketAddress address, WorkNodeId startNode, UUID owner, Instant at) {
        return new WorkBracket(id, jobId, address, startNode, owner, at, true, 0, null, null);
    }

    public static WorkBracket nestedIn(
            BracketId id,
            JobId jobId,
            BracketAddress address,
            WorkNodeId startNode,
            UUID marker,
            BracketId parent,
            Instant at) {
        Objects.requireNonNull(parent, "a nested bracket names the bracket it sits inside");
        return new WorkBracket(id, jobId, address, startNode, marker, at, false, 1, parent, null);
    }

    public static WorkBracket continuing(
            BracketId id,
            WorkBracket predecessor,
            BracketAddress addressWithNewPerformer,
            WorkNodeId startNode,
            UUID newPerformer,
            boolean causedByDeactivation,
            Instant at) {
        Objects.requireNonNull(predecessor, "a handover names what it continues");

        if (!predecessor.address.workType().equals(addressWithNewPerformer.workType())) {
            throw new IllegalArgumentException("R14.3 - a handover inherits its work type; "
                    + predecessor.address.workType() + " may not become " + addressWithNewPerformer.workType());
        }

        WorkBracket successor = new WorkBracket(
                id,
                predecessor.jobId,
                addressWithNewPerformer,
                startNode,
                newPerformer,
                at,
                false,
                predecessor.depth,
                predecessor.parentBracketId,
                predecessor.id);
        successor.disrupted = causedByDeactivation;
        return successor;
    }

    public static WorkBracket rehydrated(
            BracketId id,
            JobId jobId,
            BracketAddress address,
            WorkNodeId openedByNode,
            UUID closureRight,
            BracketState state,
            CloseKind closeKind,
            OutputKind outputKind,
            String outputValue,
            WorkNodeId closedByNode,
            BracketId parentBracketId,
            BracketId continuesBracketId,
            int depth,
            boolean boundary,
            boolean disrupted,
            Instant nudgedAt,
            boolean answeredNudge,
            Instant openedAt,
            Instant closedAt,
            Instant lastActivityAt) {
        WorkBracket bracket = new WorkBracket(
                id,
                jobId,
                address,
                openedByNode,
                closureRight,
                openedAt,
                boundary,
                depth,
                parentBracketId,
                continuesBracketId);
        bracket.state = Objects.requireNonNull(state);
        bracket.closeKind = closeKind;
        bracket.outputKind = outputKind;
        bracket.outputValue = outputValue;
        bracket.closedByNode = closedByNode;
        bracket.disrupted = disrupted;
        bracket.nudgedAt = nudgedAt;
        bracket.answeredNudge = answeredNudge;
        bracket.closedAt = closedAt;
        bracket.lastActivityAt = Objects.requireNonNull(lastActivityAt);
        return bracket;
    }

    public void delivered(WorkNodeId endNode, OutputKind kind, String value, Instant at) {
        if (kind == null || value == null || value.isBlank()) {
            throw new IllegalArgumentException("R6.1 - a delivery names what it produced");
        }
        close(endNode, CloseKind.DELIVERED, at);
        this.outputKind = kind;
        this.outputValue = value.trim();
    }

    public void done(WorkNodeId endNode, Instant at) {
        close(endNode, CloseKind.DONE, at);
    }

    public void dropped(WorkNodeId endNode, String reason, Instant at) {
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("a drop says why; one that does not teaches nothing");
        }
        close(endNode, CloseKind.DROPPED, at);
        this.outputValue = reason.trim();
    }

    public void forceClosed(Instant at) {
        close(null, CloseKind.OVERRIDE, at);
    }

    public void closedByParent(Instant at) {
        close(null, CloseKind.PARENT_CLOSED, at);
    }

    public void jobEnded(WorkNodeId endNode, Instant at) {
        if (!boundary) {
            throw new IllegalStateException("R15.3 - bracket " + id.value()
                    + " is work, not the job boundary; only the boundary carries JOB_END, and a piece of "
                    + "work claiming it would say the engagement ended when one person finished one thing");
        }
        close(endNode, CloseKind.JOB_END, at);
    }

    public void handedOver(Instant at) {
        close(null, CloseKind.HANDED_OVER, at);
    }

    public void closedByCadence(Instant at) {
        close(null, CloseKind.CADENCE_CLOSED, at);
    }

    public void lapsed(Instant at) {
        if (boundary) {
            throw new IllegalStateException(
                    "D8 - the job boundary is not work and does not lapse; lapsing it would close the whole engagement");
        }
        if (nudgedAt == null) {
            throw new IllegalStateException(
                    "R8 - a bracket lapses only after its one nudge; lapsing an un-nudged bracket is a silent abandonment");
        }
        if (answeredNudge) {
            throw new IllegalStateException(
                    "R8.1 - this bracket saw activity after its nudge; lapsing it would delete live work from the evidence");
        }
        close(null, CloseKind.LAPSED, at);
        this.state = BracketState.LAPSED;
    }

    public void nudged(Instant at) {
        if (boundary) {
            throw new IllegalStateException("D8 - nudging the boundary asks somebody to finish opening a job");
        }
        if (nudgedAt != null) {
            throw new IllegalStateException("R8 - bracket " + id.value() + " has already had its one nudge");
        }
        if (state.isTerminal()) {
            throw new IllegalStateException("nothing is nudged about a bracket that has already ended");
        }
        this.nudgedAt = Objects.requireNonNull(at);
    }

    public void touched(Instant at) {
        if (state.isTerminal()) {
            throw new IllegalStateException(
                    "bracket " + id.value() + " ended as " + closeKind + "; work that returns opens a new bracket");
        }
        this.lastActivityAt = Objects.requireNonNull(at);
        if (nudgedAt != null) {
            this.answeredNudge = true;
        }
    }

    public void closureTransfersTo(UUID person) {
        refuseIfEnded();
        this.closureRight = Objects.requireNonNull(person, "R5.1 - somebody must always hold the right to close this");
        this.closureStandsInFor = null;
    }

    public void closureWalksUpTo(UUID manager) {
        Objects.requireNonNull(manager, "R5.1 - somebody must always hold the right to close this");
        refuseIfEnded();

        if (closureStandsInFor == null) {
            this.closureStandsInFor = closureRight;
        }

        this.closureRight = manager;
    }

    public void closureReturnsTo(UUID person) {
        Objects.requireNonNull(person, "somebody came back");
        refuseIfEnded();

        if (person.equals(closureStandsInFor)) {
            this.closureRight = person;
            this.closureStandsInFor = null;
        }
    }

    public Optional<UUID> closureStandsInFor() {
        return Optional.ofNullable(closureStandsInFor);
    }

    public void restoreStandIn(UUID standsInFor) {
        this.closureStandsInFor = standsInFor;
    }

    public void restoreWorkTypeOverridden(boolean overridden) {
        this.workTypeOverridden = overridden;
    }

    public boolean mayBeClosedBy(UUID person) {
        return closureRight.equals(person);
    }

    public void claimedBy(UUID claimer) {
        Objects.requireNonNull(claimer, "R2.1 - a claim names who is taking the work on");
        refuseIfEnded();

        if (boundary) {
            throw new IllegalStateException("R4a - the job boundary is the engagement, not work going spare; "
                    + "there is nothing about it to claim");
        }

        if (!address.isUnclaimed()) {
            throw new IllegalStateException("R2.1 - bracket " + id.value() + " is already "
                    + address.performerId() + "'s work; claiming it would put two people's durations in "
                    + "one thread, which is the guarantee the five-part key exists for");
        }

        this.address = address.performedBy(claimer);
        this.closureRight = claimer;
    }

    public void merged(Instant at) {
        close(null, CloseKind.MERGED, at);
    }

    public boolean countsAsPatternEvidence() {
        return !disrupted && !boundary && closeKind != null && closeKind.countsAsPatternEvidence();
    }

    private void close(WorkNodeId endNode, CloseKind kind, Instant at) {
        refuseIfEnded();
        this.closedByNode = endNode;
        this.closeKind = Objects.requireNonNull(kind);
        this.closedAt = Objects.requireNonNull(at, "a bracket that ended, ended at a time");
        this.state = BracketState.CLOSED;
        this.lastActivityAt = at;
    }

    private void refuseIfEnded() {
        if (state.isTerminal()) {
            throw new IllegalStateException("bracket " + id.value() + " already ended as " + closeKind
                    + "; work that returns opens a new bracket at the same address");
        }
    }

    private static int requireDepth(int depth) {
        if (depth < 0 || depth > 1) {
            throw new IllegalArgumentException(
                    "R9 - two levels, and there is no third; depth " + depth + " was asked for");
        }
        return depth;
    }

    public BracketId id() {
        return id;
    }

    public JobId jobId() {
        return jobId;
    }

    public BracketAddress address() {
        return address;
    }

    public BracketState state() {
        return state;
    }

    public UUID closureRight() {
        return closureRight;
    }

    public WorkNodeId openedByNode() {
        return openedByNode;
    }

    public Optional<WorkNodeId> closedByNode() {
        return Optional.ofNullable(closedByNode);
    }

    public Optional<CloseKind> closeKind() {
        return Optional.ofNullable(closeKind);
    }

    public Optional<OutputKind> outputKind() {
        return Optional.ofNullable(outputKind);
    }

    public Optional<String> outputValue() {
        return Optional.ofNullable(outputValue);
    }

    public Optional<BracketId> parentBracketId() {
        return Optional.ofNullable(parentBracketId);
    }

    public Optional<BracketId> continuesBracketId() {
        return Optional.ofNullable(continuesBracketId);
    }

    public int depth() {
        return depth;
    }

    public boolean isBoundary() {
        return boundary;
    }

    public void workTypeWasCorrected() {
        this.workTypeOverridden = true;
    }

    public boolean isWorkTypeOverridden() {
        return workTypeOverridden;
    }

    public boolean isDisrupted() {
        return disrupted;
    }

    public Optional<Instant> nudgedAt() {
        return Optional.ofNullable(nudgedAt);
    }

    public boolean answeredNudge() {
        return answeredNudge;
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

    public void waitingOnSomething(boolean blocked) {
        refuseIfEnded();
        this.state = blocked ? BracketState.WAITING : BracketState.OPEN;
    }
}
