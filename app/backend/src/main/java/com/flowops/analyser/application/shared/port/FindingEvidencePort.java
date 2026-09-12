package com.flowops.analyser.application.shared.port;

import java.util.List;
import java.util.UUID;

public interface FindingEvidencePort {
    Evidence evidenceFor(UUID findingId);

    record Node(
            UUID id,
            UUID jobId,
            String jobName,
            UUID conversationId,
            UUID messageId,
            String messageText,
            String text,
            String title,
            String detail,
            List<String> checklist,
            String workType,
            String nodeRole,
            Person marker,
            Person performer) {}

    record Person(UUID id, String name, String role, String department) {}

    record Template(
            UUID id, String title, String status, String workType, String responsibleRole, List<String> checklist) {}

    record Job(UUID id, String name, String status) {}

    record Spread(List<String> departments, List<String> roles, boolean crossesDepartments) {}

    record Bracket(
            UUID id,
            UUID jobId,
            String jobName,
            String workType,
            String projectLabel,
            String state,
            String closeKind,
            String outputValue,
            java.time.Instant openedAt,
            java.time.Instant closedAt,
            Long minutes) {}

    record Wait(
            UUID id,
            UUID bracketId,
            UUID jobId,
            String jobName,
            String kind,
            String reason,
            java.time.Instant openedAt,
            java.time.Instant satisfiedAt,
            java.time.Instant cancelledAt,
            Long days) {}

    record Evidence(
            List<Node> nodes,
            List<Template> templates,
            List<Job> jobs,
            List<Bracket> brackets,
            List<Wait> waits,
            Spread spread) {}
}
