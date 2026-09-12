package com.flowops.discovery.application.assignorphan;

import com.flowops.discovery.application.shared.exception.NodeIsNotOrphanException;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.TrackAlreadyClosedException;
import com.flowops.discovery.application.shared.exception.UnknownTrackException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AssignOrphanService implements AssignOrphanUseCase {
    private static final int OPEN_ENGAGEMENTS_SWEPT = 200;

    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final Clock clock;

    public AssignOrphanService(IdentifyCallerPort caller, WorkGraphPort graph, Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public List<Orphan> awaitingPlacement() {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        List<Orphan> waiting = new ArrayList<>();
        for (Job engagement : graph.recentlyTouchedOpenJobs(OPEN_ENGAGEMENTS_SWEPT)) {
            for (WorkNode stranded : graph.orphansOf(engagement.id())) {
                waiting.add(new Orphan(
                        stranded.id().value(),
                        engagement.id().value(),
                        engagement.name(),
                        stranded.text(),
                        stranded.direction(),
                        stranded.createdAt(),
                        stranded.creatorId()));
            }
        }
        return List.copyOf(waiting);
    }

    @Override
    @Transactional
    public void place(UUID nodeId, UUID trackId) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNode stranded = graph.findNode(WorkNodeId.of(nodeId))
                .orElseThrow(() -> new UnknownWorkNodeException("no unit of work " + nodeId));

        if (!stranded.isOrphan()) {
            throw new NodeIsNotOrphanException("unit of work " + nodeId + " already belongs to a thread");
        }

        Track thread = graph.findTrack(TrackId.of(trackId))
                .orElseThrow(() -> new UnknownTrackException("no thread of work " + trackId));

        if (!thread.job().equals(stranded.job())) {
            throw new UnknownTrackException("thread of work " + trackId + " is not in engagement "
                    + stranded.job().value());
        }

        if (thread.state().isClosed()) {
            throw new TrackAlreadyClosedException("thread of work " + trackId + " is closed");
        }

        stranded.placedIn(thread.id());
        thread.nodeAdded(clock.instant());
        graph.save(stranded);
        graph.save(thread);
    }
}
