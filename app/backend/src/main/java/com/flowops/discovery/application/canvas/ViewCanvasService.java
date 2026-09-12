package com.flowops.discovery.application.canvas;

import com.flowops.discovery.application.markmessage.UnknownJobException;
import com.flowops.discovery.application.shared.exception.NotAuthenticatedException;
import com.flowops.discovery.application.shared.port.IdentifyCallerPort;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ViewCanvasService implements ViewCanvasUseCase {
    private final IdentifyCallerPort caller;
    private final CanvasReadPort canvas;

    public ViewCanvasService(IdentifyCallerPort caller, CanvasReadPort canvas) {
        this.caller = caller;
        this.canvas = canvas;
    }

    @Override
    @Transactional(readOnly = true)
    public Canvas of(UUID jobId) {
        caller.currentCaller().orElseThrow(NotAuthenticatedException::new);

        CanvasReadPort.JobHeader engagement = canvas.jobHeader(jobId).orElseThrow(() -> new UnknownJobException(jobId));

        Map<UUID, List<Phase>> phasesByNode = phasesByNode(jobId);
        Map<UUID, List<Card>> cardsByLane = cardsByLane(jobId, phasesByNode);
        Map<UUID, List<Loop>> loopsByLane = loopsByLane(jobId);

        List<Lane> lanes = new ArrayList<>();
        for (CanvasReadPort.LaneRow lane : canvas.lanesOf(jobId)) {
            lanes.add(new Lane(
                    lane.trackId(),
                    lane.fromRoleName(),
                    lane.toRoleName(),
                    lane.state().name(),
                    lane.completeness() == null ? null : lane.completeness().name(),
                    lane.closeReason() == null ? null : lane.closeReason().name(),
                    lane.weaklyKeyed(),
                    cardsByLane.getOrDefault(lane.trackId(), List.of()),
                    loopsByLane.getOrDefault(lane.trackId(), List.of())));
        }
        return new Canvas(engagement.jobId(), engagement.name(), List.copyOf(lanes));
    }

    private Map<UUID, List<Phase>> phasesByNode(UUID jobId) {
        Map<UUID, List<Phase>> byNode = new LinkedHashMap<>();
        for (CanvasReadPort.PhaseRow row : canvas.phasesOf(jobId)) {
            byNode.computeIfAbsent(row.nodeId(), node -> new ArrayList<>())
                    .add(new Phase(row.phase().name(), row.ms()));
        }
        return byNode;
    }

    private Map<UUID, List<Card>> cardsByLane(UUID jobId, Map<UUID, List<Phase>> phasesByNode) {
        Map<UUID, List<Card>> byLane = new LinkedHashMap<>();
        for (CanvasReadPort.CardRow row : canvas.cardsOf(jobId)) {
            byLane.computeIfAbsent(row.trackId(), lane -> new ArrayList<>())
                    .add(new Card(
                            row.nodeId(),
                            row.title(),
                            row.kind().name(),
                            row.direction().name(),
                            row.outputType() == null ? null : row.outputType().name(),
                            row.templated(),
                            phasesByNode.getOrDefault(row.nodeId(), List.of()),
                            row.conversationId(),
                            row.messageId()));
        }
        return byLane;
    }

    private Map<UUID, List<Loop>> loopsByLane(UUID jobId) {
        Map<UUID, List<Loop>> byLane = new LinkedHashMap<>();
        for (CanvasReadPort.LoopRow row : canvas.loopsOf(jobId)) {
            byLane.computeIfAbsent(row.trackId(), lane -> new ArrayList<>())
                    .add(new Loop(
                            row.memberNodeIds(),
                            row.cycleCount(),
                            row.exitCondition() == null
                                    ? null
                                    : row.exitCondition().name()));
        }
        return byLane;
    }
}
