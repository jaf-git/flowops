package com.flowops.discovery.api.dto;

import com.flowops.discovery.application.canvas.ViewCanvasUseCase;
import java.util.List;
import java.util.UUID;

public record CanvasResponse(UUID jobId, String jobName, List<Lane> lanes) {
    public static CanvasResponse of(ViewCanvasUseCase.Canvas canvas) {
        return new CanvasResponse(
                canvas.jobId(),
                canvas.jobName(),
                canvas.lanes().stream().map(Lane::of).toList());
    }

    public record Lane(
            UUID trackId,
            String fromRoleName,
            String toRoleName,
            String state,
            String completeness,
            String closeReason,
            boolean weaklyKeyed,
            List<Card> cards,
            List<Loop> loops) {
        static Lane of(ViewCanvasUseCase.Lane lane) {
            return new Lane(
                    lane.trackId(),
                    lane.fromRoleName(),
                    lane.toRoleName(),
                    lane.state(),
                    lane.completeness(),
                    lane.closeReason(),
                    lane.weaklyKeyed(),
                    lane.cards().stream().map(Card::of).toList(),
                    lane.loops().stream().map(Loop::of).toList());
        }
    }

    public record Card(
            UUID nodeId,
            String title,
            String kind,
            String direction,
            String outputType,
            boolean templated,
            List<Phase> phases,
            UUID conversationId,
            UUID messageId) {
        static Card of(ViewCanvasUseCase.Card card) {
            return new Card(
                    card.nodeId(),
                    card.title(),
                    card.kind(),
                    card.direction(),
                    card.outputType(),
                    card.templated(),
                    card.phases().stream().map(Phase::of).toList(),
                    card.conversationId(),
                    card.messageId());
        }
    }

    public record Phase(String phase, long ms) {
        static Phase of(ViewCanvasUseCase.Phase phase) {
            return new Phase(phase.phase(), phase.ms());
        }
    }

    public record Loop(List<UUID> memberNodeIds, int cycleCount, String exitCondition) {
        static Loop of(ViewCanvasUseCase.Loop loop) {
            return new Loop(loop.memberNodeIds(), loop.cycleCount(), loop.exitCondition());
        }
    }
}
