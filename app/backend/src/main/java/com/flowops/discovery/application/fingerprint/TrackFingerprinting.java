package com.flowops.discovery.application.fingerprint;

import com.flowops.discovery.application.shared.port.NodePhasePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.model.Fingerprint;
import com.flowops.discovery.domain.model.NodePhase;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.WorkNode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TrackFingerprinting {
    private final WorkGraphPort graph;
    private final NodePhasePort phases;

    public TrackFingerprinting(WorkGraphPort graph, NodePhasePort phases) {
        this.graph = graph;
        this.phases = phases;
    }

    public void fingerprintTheThread(Track thread) {
        if (!thread.qualifiesForDiscovery()) {
            return;
        }

        List<WorkNode> counted = countedNodesOf(thread);
        for (int position = 0; position < counted.size(); position++) {
            WorkNode node = counted.get(position);
            WorkNode before = position == 0 ? null : counted.get(position - 1);
            WorkNode after = position == counted.size() - 1 ? null : counted.get(position + 1);

            node.fingerprintedAs(fingerprintOf(node, before, after, position + 1));
            graph.save(node);
        }
    }

    private List<WorkNode> countedNodesOf(Track thread) {
        List<WorkNode> counted = new ArrayList<>();
        for (WorkNode node : graph.nodesOf(thread.id())) {
            if (node.kind() != NodeKind.JOB_START && !node.isQuery()) {
                counted.add(node);
            }
        }
        return counted;
    }

    private Fingerprint fingerprintOf(WorkNode node, WorkNode before, WorkNode after, int positionInTrack) {
        UUID cameFrom = before == null ? null : roleThatDidIt(before);
        return new Fingerprint(
                roleThatDidIt(node),
                medianWorkPhaseOf(node),
                node.outputType().orElse(null),
                cameFrom,
                cameFrom == null ? null : before.direction(),
                after == null ? null : roleThatDidIt(after),
                positionInTrack);
    }

    private UUID roleThatDidIt(WorkNode node) {
        return node.performerRoleId().orElse(null);
    }

    private Duration medianWorkPhaseOf(WorkNode node) {
        if (!node.contributesADuration()) {
            return null;
        }

        List<Duration> worked = new ArrayList<>();
        for (NodePhase phase : phases.phasesOf(node.id())) {
            if (phase.kind() == PhaseKind.WORK) {
                phase.elapsed().ifPresent(worked::add);
            }
        }
        if (worked.isEmpty()) {
            return null;
        }

        worked.sort(Comparator.naturalOrder());
        int middle = worked.size() / 2;
        return worked.size() % 2 == 1
                ? worked.get(middle)
                : worked.get(middle - 1).plus(worked.get(middle)).dividedBy(2);
    }
}
