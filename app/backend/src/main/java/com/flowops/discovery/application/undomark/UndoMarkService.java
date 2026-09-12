package com.flowops.discovery.application.undomark;

import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.UndoWindowClosedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkBracketPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.model.JobId;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UndoMarkService implements UndoMarkUseCase {
    private static final Duration UNDO_WINDOW = Duration.ofSeconds(10);

    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final WorkBracketPort brackets;
    private final Clock clock;

    public UndoMarkService(IdentifyCallerPort caller, WorkGraphPort graph, WorkBracketPort brackets, Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.brackets = brackets;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void execute(UUID nodeId) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNode node = graph.findNode(WorkNodeId.of(nodeId))
                .orElseThrow(() -> new UnknownWorkNodeException("there is no unit of work with the identifier " + nodeId
                        + ", so there is nothing to take back"));

        if (clock.instant().isAfter(node.createdAt().plus(UNDO_WINDOW))) {
            throw new UndoWindowClosedException("unit of work " + nodeId + " was marked at " + node.createdAt()
                    + ", which is more than " + UNDO_WINDOW.toSeconds() + " seconds ago");
        }

        TrackId thread = node.track().orElse(null);
        JobId engagement = node.job();
        boolean itOpenedTheEngagement = node.kind() == NodeKind.JOB_START;

        boolean theEngagementGoesToo = itOpenedTheEngagement && !graph.holdsOtherNodes(engagement, node.id());

        if (itOpenedTheEngagement && !theEngagementGoesToo) {
            throw new TheEngagementHoldsOtherWorkException(nodeId);
        }

        if (theEngagementGoesToo) {
            brackets.withdrawBracketsOf(engagement);
        }

        graph.deleteNode(node.id());

        if (thread != null && graph.nodesOf(thread).isEmpty()) {
            graph.deleteTrack(thread);
        }

        if (theEngagementGoesToo) {
            graph.deleteJob(engagement);
        }
    }
}
