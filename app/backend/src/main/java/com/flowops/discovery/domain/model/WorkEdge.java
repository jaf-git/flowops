package com.flowops.discovery.domain.model;

import com.flowops.discovery.domain.enums.EdgeKind;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public final class WorkEdge {
    private final UUID id;
    private final WorkNodeId fromNode;
    private final WorkNodeId toNode;
    private final Instant firstSeen;

    private EdgeKind kind;
    private BigDecimal weight;
    private int observationCount;
    private BigDecimal consistencyRatio;
    private Instant lastSeen;
    private boolean verifiedByOwner;

    private WorkEdge(
            UUID id,
            WorkNodeId fromNode,
            WorkNodeId toNode,
            EdgeKind kind,
            BigDecimal weight,
            int observationCount,
            BigDecimal consistencyRatio,
            Instant firstSeen,
            Instant lastSeen,
            boolean verifiedByOwner) {
        this.id = Objects.requireNonNull(id, "an edge needs an identity");
        this.fromNode = Objects.requireNonNull(fromNode);
        this.toNode = Objects.requireNonNull(toNode);
        this.kind = Objects.requireNonNull(kind, "an edge that does not say what it is cannot be read");
        this.weight = weight;
        this.observationCount = observationCount;
        this.consistencyRatio = consistencyRatio;
        this.firstSeen = Objects.requireNonNull(firstSeen);
        this.lastSeen = Objects.requireNonNull(lastSeen);
        this.verifiedByOwner = verifiedByOwner;
        if (fromNode.equals(toNode)) {
            throw new IllegalArgumentException("a unit of work does not follow itself; a repeated pair is a loop");
        }
    }

    public static WorkEdge firstObserved(UUID id, WorkNodeId fromNode, WorkNodeId toNode, EdgeKind kind, Instant at) {
        return new WorkEdge(id, fromNode, toNode, kind, null, 1, null, at, at, false);
    }

    public static WorkEdge rehydrated(
            UUID id,
            WorkNodeId fromNode,
            WorkNodeId toNode,
            EdgeKind kind,
            BigDecimal weight,
            int observationCount,
            BigDecimal consistencyRatio,
            Instant firstSeen,
            Instant lastSeen,
            boolean verifiedByOwner) {
        return new WorkEdge(
                id,
                fromNode,
                toNode,
                kind,
                weight,
                observationCount,
                consistencyRatio,
                firstSeen,
                lastSeen,
                verifiedByOwner);
    }

    public void observedAgain(Instant at) {
        this.observationCount++;
        this.lastSeen = Objects.requireNonNull(at);
    }

    public void weightedAt(BigDecimal value) {
        this.weight = Objects.requireNonNull(value, "a weight nobody computed is not a weight");
    }

    public void consistencyAt(BigDecimal ratio) {
        this.consistencyRatio = Objects.requireNonNull(ratio);
    }

    public void reclassifiedAs(EdgeKind newKind) {
        Objects.requireNonNull(newKind);
        if (verifiedByOwner && newKind != kind) {
            throw new IllegalStateException(
                    "edge " + id + " was confirmed by the owner as " + kind + "; a detector does not overrule that");
        }
        this.kind = newKind;
    }

    public void confirmedByOwner() {
        this.verifiedByOwner = true;
    }

    public UUID id() {
        return id;
    }

    public WorkNodeId fromNode() {
        return fromNode;
    }

    public WorkNodeId toNode() {
        return toNode;
    }

    public EdgeKind kind() {
        return kind;
    }

    public BigDecimal weight() {
        return weight;
    }

    public int observationCount() {
        return observationCount;
    }

    public BigDecimal consistencyRatio() {
        return consistencyRatio;
    }

    public Instant firstSeen() {
        return firstSeen;
    }

    public Instant lastSeen() {
        return lastSeen;
    }

    public boolean verifiedByOwner() {
        return verifiedByOwner;
    }
}
