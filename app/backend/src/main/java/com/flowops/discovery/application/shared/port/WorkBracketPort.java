package com.flowops.discovery.application.shared.port;

import com.flowops.discovery.domain.model.BracketAddress;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.WorkBracket;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkBracketPort {
    BracketId nextBracketId();

    UUID nextWaitId();

    void save(WorkBracket bracket);

    Optional<WorkBracket> find(BracketId id);

    Optional<WorkBracket> findOpenAt(JobId job, BracketAddress address);

    List<WorkBracket> findLiveIn(JobId job);

    List<WorkBracket> liveWorkIn(JobId job);

    Optional<WorkBracket> boundaryOf(JobId job);

    List<WorkBracket> liveWorkEverywhere();

    void withdrawBracketsOf(JobId job);

    void mergeNodesInto(BracketId source, BracketId target);

    void publishArtifact(WorkBracket delivered, UUID counterparty);

    List<WorkBracket> continuationChainFrom(BracketId head);

    void save(com.flowops.discovery.domain.model.WorkNodeWait wait);

    java.util.Optional<com.flowops.discovery.domain.model.WorkNodeWait> findWait(UUID id);

    List<com.flowops.discovery.domain.model.WorkNodeWait> openWaitsHeldBy(BracketId bracket);

    List<com.flowops.discovery.domain.model.WorkNodeWait> openWaitsOn(BracketId bracket);

    List<com.flowops.discovery.domain.model.WorkNodeWait> waitsPastTheirExpectedDate(Instant now);

    List<com.flowops.discovery.domain.model.WorkNodeWait> externalWaitsOpenSince(Instant before);

    List<WorkBracket> awaitingTheirOneNudge(Instant idleSince);

    List<WorkBracket> readyToLapse(Instant nudgedBefore);

    Optional<WorkNodeId> rootNodeOf(JobId job);

    List<WorkNodeId> chainOf(JobId job, UUID performer);

    BracketId mainLevelAncestorOf(BracketId bracket);

    boolean placeNode(
            WorkNodeId node,
            BracketId bracket,
            com.flowops.discovery.domain.enums.NodeRole role,
            BracketAddress address,
            UUID marker,
            WorkNodeId parent);

    WorkNodeId appendEndNode(WorkBracket bracket, UUID marker, Instant at);

    WorkNodeId appendStartNode(
            WorkBracket successor, WorkNodeId announcement, WorkNodeId parent, UUID marker, Instant at);
}
