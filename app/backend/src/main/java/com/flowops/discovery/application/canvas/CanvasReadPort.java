package com.flowops.discovery.application.canvas;

import com.flowops.discovery.domain.enums.CloseReason;
import com.flowops.discovery.domain.enums.Completeness;
import com.flowops.discovery.domain.enums.Direction;
import com.flowops.discovery.domain.enums.LoopExit;
import com.flowops.discovery.domain.enums.NodeKind;
import com.flowops.discovery.domain.enums.OutputType;
import com.flowops.discovery.domain.enums.PhaseKind;
import com.flowops.discovery.domain.enums.TrackState;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CanvasReadPort {
    Optional<JobHeader> jobHeader(UUID jobId);

    List<LaneRow> lanesOf(UUID jobId);

    List<CardRow> cardsOf(UUID jobId);

    List<PhaseRow> phasesOf(UUID jobId);

    List<LoopRow> loopsOf(UUID jobId);

    record JobHeader(UUID jobId, String name) {}

    record LaneRow(
            UUID trackId,
            String fromRoleName,
            String toRoleName,
            TrackState state,
            Completeness completeness,
            CloseReason closeReason,
            boolean weaklyKeyed) {}

    record CardRow(
            UUID trackId,
            UUID nodeId,
            String title,
            NodeKind kind,
            Direction direction,
            OutputType outputType,
            boolean templated,
            UUID conversationId,
            UUID messageId) {}

    record PhaseRow(UUID nodeId, PhaseKind phase, long ms) {}

    record LoopRow(UUID trackId, List<UUID> memberNodeIds, int cycleCount, LoopExit exitCondition) {}
}
