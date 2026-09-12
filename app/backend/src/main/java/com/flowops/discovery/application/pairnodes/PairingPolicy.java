package com.flowops.discovery.application.pairnodes;

import com.flowops.discovery.application.shared.port.DiscoveryThresholdPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Component;

@Component
public class PairingPolicy {
    private final WorkGraphPort graph;
    private final DiscoveryThresholdPort thresholds;

    public PairingPolicy(WorkGraphPort graph, DiscoveryThresholdPort thresholds) {
        this.graph = graph;
        this.thresholds = thresholds;
    }

    public Optional<WorkNodeId> pairOnCompletion(WorkNode completion) {
        if (completion.isQuery() || completion.direction() != Direction.COMPLETION) {
            return Optional.empty();
        }

        Optional<TrackId> thread = completion.track();
        if (thread.isEmpty()) {
            return Optional.empty();
        }

        List<WorkNode> candidates = graph.nodesOf(thread.get()).stream()
                .filter(PairingPolicy::isARequestStillOpen)
                .toList();
        if (candidates.isEmpty()) {
            return Optional.empty();
        }

        WorkNodeId newest = candidates.getLast().id();

        WorkNode best = null;
        int bestScore = 0;
        for (WorkNode candidate : candidates) {
            int score = PairingSignals.between(
                            candidate, completion, candidate.id().equals(newest))
                    .matched();
            if (score > bestScore) {
                bestScore = score;
                best = candidate;
            }
        }

        int toPairSilently = thresholds.thresholds().pairingSignalsToAuto();
        if (best == null || bestScore < toPairSilently) {
            return Optional.empty();
        }
        return Optional.of(best.id());
    }

    private static boolean isARequestStillOpen(WorkNode node) {
        return node.direction() == Direction.REQUEST && !node.state().isTerminal();
    }
}
