package com.flowops.discovery.application.correcttrack;

import com.flowops.discovery.application.proposetype.ProposeTypeUseCase;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.TrackAlreadyClosedException;
import com.flowops.discovery.application.shared.exception.UnknownTrackException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CorrectTrackService implements CorrectTrackUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final ProposeTypeUseCase catalogue;
    private final Clock clock;

    public CorrectTrackService(
            IdentifyCallerPort caller, WorkGraphPort graph, ProposeTypeUseCase catalogue, Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.catalogue = catalogue;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void moveToLane(UUID nodeId, UUID trackId) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNode card = graph.findNode(WorkNodeId.of(nodeId))
                .orElseThrow(() -> new UnknownWorkNodeException("no unit of work " + nodeId));

        Track lane = graph.findTrack(TrackId.of(trackId))
                .orElseThrow(() -> new UnknownTrackException("no thread of work " + trackId));

        if (!lane.job().equals(card.job())) {
            throw new UnknownTrackException("thread of work " + trackId + " is not in engagement "
                    + card.job().value());
        }

        if (lane.state().isClosed()) {
            throw new TrackAlreadyClosedException("thread of work " + trackId + " is closed");
        }

        if (card.track().map(TrackId::value).filter(trackId::equals).isPresent()) {
            return;
        }

        card.placedIn(lane.id());
        lane.nodeAdded(clock.instant());
        graph.save(card);
        graph.save(lane);

        catalogue.execute();
    }
}
