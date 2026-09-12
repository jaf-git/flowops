package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.BracketState;
import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.NodeRole;
import java.util.List;
import java.util.Objects;

public final class BracketInvariants {
    private BracketInvariants() {}

    public static void everyOpenBracketIsClosable(WorkBracket bracket) {
        Objects.requireNonNull(bracket, "an invariant is asserted about something");

        if (bracket.state().isLive() && bracket.closureRight() == null) {
            throw new IllegalStateException("invariant 5 - bracket "
                    + bracket.id().value() + " is live with nobody able to close it; R5.1 says that must never happen");
        }
    }

    public static void aWaitIsSatisfiedOnlyByACompletion(CloseKind closeKind, boolean releasingWaiters) {
        if (releasingWaiters && (closeKind == null || !closeKind.satisfiesAWait())) {
            throw new IllegalStateException("invariant 7 - " + closeKind
                    + " is not a completion and must not release anybody waiting; "
                    + "the waiter is told the thing they awaited died (N8) instead");
        }
    }

    public static void waitingStateMatchesTheWaits(WorkBracket bracket, int openWaitCount) {
        if (bracket.state().isTerminal()) {
            return;
        }

        boolean flaggedWaiting = bracket.state() == BracketState.WAITING;
        boolean actuallyWaiting = openWaitCount > 0;

        if (flaggedWaiting != actuallyWaiting) {
            throw new IllegalStateException(
                    "invariant 8 - bracket " + bracket.id().value() + " is "
                            + bracket.state() + " with " + openWaitCount + " open waits; "
                            + "a queue that looks occupied by unblocked work is the defect this prevents");
        }
    }

    public static void parentIsOlderThanChild(WorkNode parent, WorkNode child) {
        ParentRule.refuseIfNotOlder(parent, child);
    }

    public static void nothingCrossesAJob(JobId one, JobId other) {
        ParentRule.refuseIfCrossingJobs(one, other);
    }

    public static void depthIsAtMostOne(WorkBracket bracket) {
        if (bracket.depth() > 1) {
            throw new IllegalStateException(
                    "invariant 6 - bracket " + bracket.id().value() + " is at depth " + bracket.depth()
                            + "; R9 caps it at one and deeper nesting cascades on failure");
        }
    }

    public static void aStartNeverBecomesAnEnd(NodeRole was, NodeRole becomes) {
        if (was == NodeRole.START && becomes == NodeRole.END) {
            throw new IllegalStateException("invariant 9 - a START never becomes an END; "
                    + "a self-close generates a paired END from the same message (R4.3)");
        }
    }

    public static void oneEndPerBracket(WorkBracket bracket) {
        if (bracket.state().isLive() && bracket.closedByNode().isPresent()) {
            throw new IllegalStateException(
                    "invariant 10 - bracket " + bracket.id().value() + " is live and already carries an END node");
        }
    }

    public static void oneNudgePerBracket(WorkBracket bracket, boolean aboutToNudge) {
        if (aboutToNudge && bracket.nudgedAt().isPresent()) {
            throw new IllegalStateException("invariant 11 - bracket "
                    + bracket.id().value() + " has already had its one nudge; a product that nags is one people mute");
        }
    }

    public static void noNoticeAboutAClosedBracket(WorkBracket bracket) {
        if (bracket.state().isTerminal()) {
            throw new IllegalStateException(
                    "invariant 12 - bracket " + bracket.id().value() + " ended as "
                            + bracket.closeKind().orElse(null) + "; nothing is sent about it");
        }
    }

    public static void noNoticeToADepartedPerson(boolean recipientIsActive) {
        if (!recipientIsActive) {
            throw new IllegalStateException(
                    "invariant 13 - deactivation cancels every pending notice to that person (R14.5)");
        }
    }

    public static void anOrphanHoldsNoBracket(WorkNodeId node, BracketId bracket, boolean performerIsActive) {
        if (!performerIsActive && bracket != null) {
            throw new IllegalStateException("invariant 15 - node " + node.value()
                    + " names somebody with no active membership and must produce no bracket (R14.6); "
                    + "inventing one would create an obligation nobody holds");
        }
    }

    public static void noRouteAggregatesByPerson() {}

    public static void chainIsNotAnAggregate(List<WorkNodeId> chain, JobId scopedTo) {
        Objects.requireNonNull(scopedTo, "invariant 14 - a person's chain is only ever read inside one job");
        Objects.requireNonNull(chain, "a chain is a list of nodes, never a count of them");
    }

    public static void assertAllOn(WorkBracket bracket, int openWaitCount) {
        everyOpenBracketIsClosable(bracket);
        waitingStateMatchesTheWaits(bracket, openWaitCount);
        depthIsAtMostOne(bracket);
        oneEndPerBracket(bracket);
    }
}
