package com.flowops.discovery.application.canvas;

import java.util.List;
import java.util.UUID;

public interface ViewCanvasUseCase {
    Canvas of(UUID jobId);

    record Canvas(UUID jobId, String jobName, List<Lane> lanes) {}

    record Lane(
            UUID trackId,
            String fromRoleName,
            String toRoleName,
            String state,
            String completeness,
            String closeReason,
            boolean weaklyKeyed,
            List<Card> cards,
            List<Loop> loops) {}

    record Card(
            UUID nodeId,
            String title,
            String kind,
            String direction,
            String outputType,
            boolean templated,
            List<Phase> phases,
            UUID conversationId,
            UUID messageId) {}

    record Phase(String phase, long ms) {}

    record Loop(List<UUID> memberNodeIds, int cycleCount, String exitCondition) {}
}
