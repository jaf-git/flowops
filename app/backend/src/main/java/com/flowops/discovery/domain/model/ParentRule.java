package com.flowops.discovery.domain.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class ParentRule {
    private ParentRule() {}

    public static WorkNodeId parentOf(WorkNodeId causingRequest, List<WorkNodeId> performersChain, WorkNodeId jobRoot) {
        Objects.requireNonNull(jobRoot, "R3.3 - every job has a root for a node to fall back to");

        if (causingRequest != null) {
            return causingRequest;
        }

        if (performersChain != null && !performersChain.isEmpty()) {
            return performersChain.get(0);
        }

        return jobRoot;
    }

    public static Optional<BracketId> attachmentFor(
            WorkBracket candidateParent, java.util.function.Function<BracketId, BracketId> mainLevelAncestorOf) {
        if (candidateParent == null) {
            return Optional.empty();
        }

        if (candidateParent.depth() == 0) {
            return Optional.of(candidateParent.id());
        }

        return Optional.of(mainLevelAncestorOf.apply(candidateParent.id()));
    }

    public static void refuseIfNotOlder(WorkNode parent, WorkNode child) {
        Objects.requireNonNull(parent, "an edge names both ends");
        Objects.requireNonNull(child, "an edge names both ends");

        if (!parent.createdAt().isBefore(child.createdAt())) {
            throw new IllegalArgumentException("R3.1 - a parent is always older than its child; "
                    + parent.id().value()
                    + " is not older than " + child.id().value()
                    + ", and edges that ignore this are how a graph acquires a cycle");
        }
    }

    public static void refuseIfCrossingJobs(JobId parentJob, JobId childJob) {
        if (!Objects.equals(parentJob, childJob)) {
            throw new IllegalArgumentException("R3.2 - no edge crosses a job; " + parentJob.value() + " and "
                    + childJob.value() + " are different engagements and must stay separable");
        }
    }

    public static WorkNodeId rootedAt(WorkNodeId jobRoot) {
        return parentOf(null, List.of(), jobRoot);
    }

    public static boolean hasWorkedHereBefore(UUID performer, List<WorkNodeId> performersChain) {
        return performer != null && performersChain != null && !performersChain.isEmpty();
    }
}
