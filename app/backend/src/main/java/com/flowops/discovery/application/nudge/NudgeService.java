package com.flowops.discovery.application.nudge;

import com.flowops.discovery.application.shared.exception.AlreadyNudgedException;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.exception.UnknownWorkNodeException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import com.flowops.discovery.application.shared.port.WorkGraphPort;
import com.flowops.discovery.domain.enums.NudgeAnswer;
import com.flowops.discovery.domain.enums.WorkNodeState;
import com.flowops.discovery.domain.model.Job;
import com.flowops.discovery.domain.model.Track;
import com.flowops.discovery.domain.model.WorkNode;
import com.flowops.discovery.domain.model.WorkNodeId;
import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NudgeService implements NudgeUseCase {
    private static final int ENGAGEMENTS_SWEPT = 40;

    private final IdentifyCallerPort caller;
    private final WorkGraphPort graph;
    private final Clock clock;

    public NudgeService(IdentifyCallerPort caller, WorkGraphPort graph, Clock clock) {
        this.caller = caller;
        this.graph = graph;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<Nudgeable> nextToAskAbout() {
        UUID me = caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        List<WorkNode> candidates = new ArrayList<>();
        for (Job engagement : graph.recentlyTouchedOpenJobs(ENGAGEMENTS_SWEPT)) {
            Optional<Track> thread = graph.findOpenTrackForPerformer(engagement.id(), me);
            thread.ifPresent(open -> candidates.addAll(graph.nodesOf(open.id())));
            candidates.addAll(graph.orphansOf(engagement.id()));
        }

        return candidates.stream()
                .filter(node -> worthAskingAbout(node, me))
                .min(Comparator.comparing(WorkNode::createdAt))
                .map(node -> new Nudgeable(node.id(), node.job(), node.track(), node.text(), node.state()));
    }

    private boolean worthAskingAbout(WorkNode node, UUID me) {
        if (node.hasBeenNudged() || node.isQuery()) {
            return false;
        }
        if (node.performerId().filter(me::equals).isEmpty()) {
            return false;
        }
        WorkNodeState state = node.state();
        return state == WorkNodeState.MARKED
                || state == WorkNodeState.ASSIGNED
                || state == WorkNodeState.SELF
                || state == WorkNodeState.IN_PROGRESS
                || state == WorkNodeState.BLOCKED;
    }

    @Override
    @Transactional
    public Answered answer(AnswerNudge command) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        WorkNodeId id = WorkNodeId.of(command.nodeId());
        WorkNode node = graph.findNode(id)
                .orElseThrow(() ->
                        new UnknownWorkNodeException("no unit of work " + command.nodeId() + " to ask anybody about"));

        if (node.hasBeenNudged()) {
            throw new AlreadyNudgedException("unit of work " + command.nodeId() + " was nudged at "
                    + node.nudgedAt().orElseThrow() + "; a second nudge teaches people to ignore the first");
        }
        node.nudged(clock.instant());

        switch (command.answer()) {
            case DROPPED -> node.lapsed(node.performerId().orElse(null));
            case WAS_A_QUESTION -> node.becameQuery();
            default -> {}
        }

        graph.save(node);
        return new Answered(node.id(), node.state(), command.answer(), command.answer() == NudgeAnswer.DONE);
    }
}
