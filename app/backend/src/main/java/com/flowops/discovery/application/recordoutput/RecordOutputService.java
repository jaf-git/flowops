package com.flowops.discovery.application.recordoutput;

import com.flowops.discovery.application.pairnodes.PairingPolicy;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.OutputAlreadyRecordedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.NodePhasePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.NodePhase;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RecordOutputService implements RecordOutputUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final NodePhasePort phases;
    private final PairingPolicy pairing;
    private final Clock clock;

    public RecordOutputService(
            IdentifyCallerPort caller, WorkGraphPort graph, NodePhasePort phases, PairingPolicy pairing, Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.phases = phases;
        this.pairing = pairing;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Recorded execute(RecordOutput command) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNodeId id = WorkNodeId.of(command.nodeId());
        WorkNode node = graph.findNode(id)
                .orElseThrow(() -> new UnknownWorkNodeException(
                        "no unit of work " + command.nodeId() + " to record an output against"));

        refuseASecondAnswer(node);

        Instant now = clock.instant();
        startIfTheWorkNeverSaidItHad(node, id);
        node.completed(command.output());

        if (command.output().closesTheNode()) {
            sealWhateverIsRunning(id, now);
            phases.open(NodePhase.started(UUID.randomUUID(), id, PhaseKind.REVIEW, null, now));
        }

        graph.save(node);

        Optional<WorkNodeId> paired =
                command.output().closesTheNode() ? pairing.pairOnCompletion(node) : Optional.empty();

        return new Recorded(node.id(), node.state(), command.output(), paired);
    }

    private void refuseASecondAnswer(WorkNode node) {
        if (node.outputType().filter(OutputType::closesTheNode).isPresent()) {
            throw new OutputAlreadyRecordedException(
                    "unit of work " + node.id().value()
                            + " already produced " + node.outputType().orElseThrow()
                            + "; the first answer stands, because two answers are two observations rather than one stated twice");
        }
    }

    private void startIfTheWorkNeverSaidItHad(WorkNode node, WorkNodeId id) {
        if (node.state() != WorkNodeState.ASSIGNED && node.state() != WorkNodeState.SELF) {
            return;
        }
        Instant began = graph.evidenceMessageSentAt(id).orElse(node.createdAt());
        node.started(began);
        phases.open(NodePhase.started(UUID.randomUUID(), id, PhaseKind.WORK, null, began));
    }

    private void sealWhateverIsRunning(WorkNodeId id, Instant now) {
        phases.openPhaseOf(id).ifPresent(running -> {
            running.seal(now.isBefore(running.startedAt()) ? running.startedAt() : now);
            phases.seal(running);
        });
    }
}
