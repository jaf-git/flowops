package com.flowops.discovery.application.digest;

import com.flowops.discovery.application.clustering.DiscoveredWorkPort;
import com.flowops.discovery.application.digest.WeeklyDigestUseCase.Decision;
import com.flowops.discovery.application.digest.WeeklyDigestUseCase.Kind;
import com.flowops.discovery.application.shared.port.TypeCataloguePort;
import com.flowops.discovery.domain.enums.TrackTypeStatus;
import com.flowops.discovery.domain.model.TrackType;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ProposalDecisionSource implements DigestDecisionSource {
    private final TypeCataloguePort catalogue;
    private final DigestReadPort figures;
    private final DiscoveredWorkPort discoveredWork;

    public ProposalDecisionSource(
            TypeCataloguePort catalogue, DigestReadPort figures, DiscoveredWorkPort discoveredWork) {
        this.catalogue = catalogue;
        this.figures = figures;
        this.discoveredWork = discoveredWork;
    }

    @Override
    public List<Ranked> since(Instant window) {
        Map<UUID, Long> workByType = figures.workMillisByType();
        Map<UUID, String> roleNames = discoveredWork.statedJobNames();

        return catalogue.typesByStatus(TrackTypeStatus.PROPOSED).stream()
                .map(type -> ranked(type, roleNames, workByType.getOrDefault(type.id(), 0L)))
                .toList();
    }

    private Ranked ranked(TrackType type, Map<UUID, String> roleNames, long workMillis) {
        Decision decision =
                new Decision(Kind.CONFIRM_A_NAME, type.id(), handOver(type, roleNames), type.occurrenceCount());
        return new Ranked(decision, (long) type.occurrenceCount() * workMillis);
    }

    private String handOver(TrackType type, Map<UUID, String> roleNames) {
        String asked = type.fromRoleId().map(roleNames::get).orElse(null);
        String does = type.toRoleId().map(roleNames::get).orElse(null);
        if (asked == null && does == null) {
            return "Work with no stated roles";
        }
        if (asked == null || asked.equals(does)) {
            return does == null ? asked : does;
        }
        return asked + " → " + does;
    }
}
