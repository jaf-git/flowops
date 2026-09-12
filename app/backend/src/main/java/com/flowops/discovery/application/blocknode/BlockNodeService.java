package com.flowops.discovery.application.blocknode;

import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.NodePhasePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.NodePhase;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BlockNodeService implements BlockNodeUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final NodePhasePort phases;
    private final Clock clock;

    public BlockNodeService(IdentifyCallerPort caller, WorkGraphPort graph, NodePhasePort phases, Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.phases = phases;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Blocked block(BlockNode command) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNodeId id = WorkNodeId.of(command.nodeId());
        WorkNode node = theWorkOr422(id, command.nodeId());

        startIfTheWorkNeverSaidItHad(node, id);
        node.blocked();

        Instant now = clock.instant();
        PhaseKind wait = command.waitingOn().phaseKind();
        sealWhateverIsRunning(id, now);
        phases.open(NodePhase.started(UUID.randomUUID(), id, wait, command.waitingOn(), now));

        graph.save(node);
        return new Blocked(node.id(), wait, command.waitingOn());
    }

    @Override
    @Transactional
    public Blocked resume(ResumeNode command) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNodeId id = WorkNodeId.of(command.nodeId());
        WorkNode node = theWorkOr422(id, command.nodeId());

        node.resumed();

        Instant now = clock.instant();
        sealWhateverIsRunning(id, now);
        phases.open(NodePhase.started(UUID.randomUUID(), id, PhaseKind.WORK, null, now));

        graph.save(node);
        return new Blocked(node.id(), PhaseKind.WORK, null);
    }

    private WorkNode theWorkOr422(WorkNodeId id, UUID asked) {
        return graph.findNode(id)
                .orElseThrow(() -> new UnknownWorkNodeException("no unit of work " + asked + " to move a clock on"));
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
