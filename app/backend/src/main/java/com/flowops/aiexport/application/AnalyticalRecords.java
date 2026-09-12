package com.flowops.aiexport.application;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class AnalyticalRecords {
    private AnalyticalRecords() {}

    public static final String SCHEMA_VERSION = "1.0";

    public record PhaseDurations(long work, long blocked, long waiting, long review) {}

    public record ReviewOutcome(Boolean firstTryApproved, int iterations) {}

    public record Transition(String from, String to, Instant at, String reason) {}

    public record TaskRecord(
            UUID taskId,
            UUID sourceTemplateId,
            String stampedFields,
            UUID processInstanceId,
            UUID instanceStepId,
            String sourceMessageRef,
            String type,
            String priority,
            Instant createdAt,
            Instant deadline,
            Instant closedAt,
            String finalState,
            PhaseDurations phaseDurationsMs,
            List<Transition> stateTransitions,
            List<String> blockReasons,
            ReviewOutcome review,
            String assigneeRef,
            String creatorRef) {}

    public record InstanceRecord(
            UUID instanceId,
            UUID sourceTemplateId,
            String name,
            String state,
            Instant startedAt,
            Instant completedAt,
            String ownerRef,
            int stepCount) {}

    public record StepRecord(
            UUID stepId,
            UUID instanceId,
            String origin,
            String plannedTitle,
            int position,
            String condition,
            List<UUID> dependsOn,
            Instant reachableSince,
            Instant closedAt,
            UUID taskId,
            Long expectedDurationMs) {}

    public record ConversionRecord(String messageRef, String conversationKind, UUID taskId, Instant convertedAt) {}
}
