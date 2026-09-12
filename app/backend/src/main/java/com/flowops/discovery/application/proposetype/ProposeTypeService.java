package com.flowops.discovery.application.proposetype;

import com.flowops.discovery.application.clustering.DiscoveredWorkPort;
import com.flowops.discovery.application.clustering.TrackClustering;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.TypeCataloguePort;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import com.flowops.discovery.domain.model.TrackType;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProposeTypeService implements ProposeTypeUseCase {
    private final IdentifyCallerPort caller;
    private final TrackClustering clustering;
    private final TypeProposal proposal;
    private final TypeCataloguePort catalogue;
    private final DiscoveredWorkPort discoveredWork;

    public ProposeTypeService(
            IdentifyCallerPort caller,
            TrackClustering clustering,
            TypeProposal proposal,
            TypeCataloguePort catalogue,
            DiscoveredWorkPort discoveredWork) {
        this.caller = caller;
        this.clustering = clustering;
        this.proposal = proposal;
        this.catalogue = catalogue;
        this.discoveredWork = discoveredWork;
    }

    @Override
    @Transactional
    public List<DiscoveredType> execute() {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        clustering.clusters().forEach(proposal::reconcile);

        Map<UUID, String> roleNames = discoveredWork.statedJobNames();
        return catalogue
                .typesByStatus(
                        TrackTypeStatus.CANDIDATE,
                        TrackTypeStatus.PROPOSED,
                        TrackTypeStatus.NAMED,
                        TrackTypeStatus.PROVISIONAL)
                .stream()
                .map(type -> asRow(type, roleNames))
                .toList();
    }

    private DiscoveredType asRow(TrackType type, Map<UUID, String> roleNames) {
        return new DiscoveredType(
                type.id(),
                type.name().orElse(null),
                type.status(),
                type.occurrenceCount(),
                type.fromRoleId().map(roleNames::get).orElse(null),
                type.toRoleId().map(roleNames::get).orElse(null),
                type.terminalOutputType().orElse(null));
    }
}
