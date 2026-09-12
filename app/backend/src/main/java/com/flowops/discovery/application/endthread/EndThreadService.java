package com.flowops.discovery.application.endthread;

import com.flowops.discovery.application.fingerprint.TrackFingerprinting;
import com.flowops.discovery.application.pairnodes.PairingPolicy;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.TrackAlreadyClosedException;
import com.flowops.discovery.application.shared.exception.UnknownTrackException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.TrackClosurePort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.CloseReason;
import com.flowops.discovery.domain.enums.Completeness;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.TrackId;
import com.flowops.discovery.domain.model.WorkNode;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EndThreadService implements EndThreadUseCase {
    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final TrackClosurePort closure;
    private final PairingPolicy pairing;
    private final TrackFingerprinting fingerprinting;
    private final Clock clock;

    public EndThreadService(
            IdentifyCallerPort caller,
            WorkGraphPort graph,
            TrackClosurePort closure,
            PairingPolicy pairing,
            TrackFingerprinting fingerprinting,
            Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.closure = closure;
        this.pairing = pairing;
        this.fingerprinting = fingerprinting;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Ended execute(EndThread command) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        TrackId id = TrackId.of(command.trackId());
        Track thread = graph.findTrack(id)
                .orElseThrow(() -> new UnknownTrackException("no thread of work " + command.trackId()));

        if (thread.state().isClosed()) {
            throw new TrackAlreadyClosedException("thread of work " + command.trackId() + " is already closed");
        }

        Completeness howMuchWasCaptured = howMuchOfItWasCaptured(id);
        thread.closed(CloseReason.TERMINAL_OUTPUT, howMuchWasCaptured, clock.instant());
        closure.close(thread);

        fingerprinting.fingerprintTheThread(thread);

        return new Ended(id, CloseReason.TERMINAL_OUTPUT, howMuchWasCaptured);
    }

    private Completeness howMuchOfItWasCaptured(TrackId thread) {
        List<WorkNode> nodes = graph.nodesOf(thread);
        if (nodes.size() <= 1) {
            return Completeness.START_ONLY;
        }

        boolean somethingWasAnswered = nodes.stream()
                .filter(node -> node.direction() == Direction.COMPLETION)
                .anyMatch(completion -> pairing.pairOnCompletion(completion).isPresent());

        return somethingWasAnswered ? Completeness.COMPLETE : Completeness.PARTIAL;
    }
}
