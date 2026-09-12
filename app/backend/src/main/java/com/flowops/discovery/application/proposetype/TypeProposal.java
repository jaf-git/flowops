package com.flowops.discovery.application.proposetype;

import com.flowops.discovery.application.clustering.TrackClustering.Cluster;
import com.flowops.discovery.application.shared.port.DiscoveryThresholdPort;
import com.flowops.discovery.application.shared.port.TypeCataloguePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TypeProposal {
    private final TypeCataloguePort catalogue;
    private final WorkGraphPort graph;
    private final DiscoveryThresholdPort thresholds;

    public TypeProposal(TypeCataloguePort catalogue, WorkGraphPort graph, DiscoveryThresholdPort thresholds) {
        this.catalogue = catalogue;
        this.graph = graph;
        this.thresholds = thresholds;
    }

    public void reconcile(Cluster cluster) {
        Optional<TrackType> alreadyKnown = existingTypeOf(cluster);
        if (alreadyKnown.map(type -> type.status().isTerminal()).orElse(false)) {
            return;
        }

        int floor = thresholds.thresholds().tracksToProposeType();
        TrackType type = alreadyKnown.orElseGet(() -> newCandidateFor(cluster));

        countTheCompletedThreads(type, cluster.completedThreads());
        letTheEvidenceDecideTheStatus(type, floor);
        catalogue.save(type);

        claimTheThreads(cluster, type.id());
    }

    private Optional<TrackType> existingTypeOf(Cluster cluster) {
        return cluster.threads().stream()
                .map(Track::trackTypeId)
                .flatMap(Optional::stream)
                .findFirst()
                .flatMap(catalogue::findType);
    }

    private TrackType newCandidateFor(Cluster cluster) {
        return TrackType.candidate(
                UUID.randomUUID(),
                cluster.shape().fromRoleId(),
                cluster.shape().toRoleId(),
                cluster.shape().terminalOutputType(),
                0);
    }

    private void countTheCompletedThreads(TrackType type, int completedThreads) {
        while (type.occurrenceCount() < completedThreads) {
            type.instanceObserved();
        }
        while (type.occurrenceCount() > completedThreads) {
            type.instanceWithdrawn();
        }
    }

    private void letTheEvidenceDecideTheStatus(TrackType type, int floor) {
        boolean clears = type.clearsTheFloor(floor);
        if (type.status() == TrackTypeStatus.CANDIDATE && clears) {
            type.proposed(floor);
        } else if (type.status() == TrackTypeStatus.NAMED && !clears) {
            type.evidenceFellBelowFloor();
        } else if (type.status() == TrackTypeStatus.PROVISIONAL && clears) {
            type.evidenceRecovered();
        }
    }

    private void claimTheThreads(Cluster cluster, UUID typeId) {
        for (Track thread : cluster.threads()) {
            if (thread.trackTypeId().isEmpty()) {
                thread.typedAs(typeId);
                graph.save(thread);
            }
        }
    }
}
