package com.flowops.discovery.application.nodetrail;

import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.NodeTrailPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewNodeTrailService implements ViewNodeTrailUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final NodeTrailPort trail;

    public ViewNodeTrailService(IdentifyCallerPort caller, WorkGraphPort graph, NodeTrailPort trail) {
        this.caller = caller;
        this.graph = graph;
        this.trail = trail;
    }

    @Override
    @Transactional(readOnly = true)
    public Trail execute(UUID nodeId) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNodeId id = WorkNodeId.of(nodeId);
        if (graph.findNode(id).isEmpty()) {
            throw new UnknownWorkNodeException("no unit of work " + nodeId + " to show the history of");
        }

        List<NodeTrailPort.RecordedMove> moves = trail.trailOf(id);
        return new Trail(id, moves, recordedFromTheStart(moves));
    }

    private static boolean recordedFromTheStart(List<NodeTrailPort.RecordedMove> moves) {
        if (moves.isEmpty()) {
            return false;
        }
        NodeTrailPort.RecordedMove first = moves.getFirst();
        return first.from().isEmpty() && (first.to() == WorkNodeState.MARKED || first.to() == WorkNodeState.QUERY);
    }
}
