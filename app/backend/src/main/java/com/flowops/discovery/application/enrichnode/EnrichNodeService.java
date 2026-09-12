package com.flowops.discovery.application.enrichnode;

import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EnrichNodeService implements EnrichNodeUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;

    public EnrichNodeService(IdentifyCallerPort caller, WorkGraphPort graph) {
        this.caller = caller;
        this.graph = graph;
    }

    @Override
    @Transactional
    public Enriched execute(Enrich command) {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNodeId id = WorkNodeId.of(command.nodeId());
        WorkNode node = graph.findNode(id)
                .orElseThrow(
                        () -> new UnknownWorkNodeException("no unit of work " + command.nodeId() + " to describe"));

        boolean accepted = node.enrichedWith(me, command.title(), command.detail(), command.checklist());

        graph.save(node);

        boolean correctable =
                !node.isSettled() && node.enrichedBy().map(me::equals).orElse(true);

        return new Enriched(node.id(), node.title(), node.detail(), node.checklist(), accepted, correctable);
    }
}
