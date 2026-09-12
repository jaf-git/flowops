package com.flowops.analyser.api.dto;

import com.flowops.analyser.application.shared.port.FindingEvidencePort;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FindingEvidenceResponse(
        @Schema(description = "The marks this finding was derived from, in identifier order") List<Node> nodes,
        @Schema(description = "Library entries this finding produced or points at") List<Template> templates,
        @Schema(description = "Engagements this finding touches") List<Job> jobs,
        @Schema(description = "Pieces of work a duration was measured over, oldest first") List<Bracket> brackets,
        @Schema(description = "What this work waited on, and whether it ever arrived") List<Wait> waits,
        @Schema(description = "How far across the company this work reaches") Spread spread) {
    public record Node(
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

    public record Person(UUID id, String name, String role, String department) {}

    public record Template(
            UUID id, String title, String status, String workType, String responsibleRole, List<String> checklist) {}

    public record Job(UUID id, String name, String status) {}

    public record Bracket(
            UUID id,
            UUID jobId,
            String jobName,
            String workType,
            String projectLabel,
            String state,
            String closeKind,
            String outputValue,
            Instant openedAt,
            Instant closedAt,
            Long minutes) {}

    public record Wait(
            UUID id,
            UUID bracketId,
            UUID jobId,
            String jobName,
            String kind,
            String reason,
            Instant openedAt,
            Instant satisfiedAt,
            Instant cancelledAt,
            Long days) {}

    public record Spread(List<String> departments, List<String> roles, boolean crossesDepartments) {}

    public static FindingEvidenceResponse of(FindingEvidencePort.Evidence evidence) {
        return new FindingEvidenceResponse(
                evidence.nodes().stream()
                        .map(node -> new Node(
                                node.id(),
                                node.jobId(),
                                node.jobName(),
                                node.conversationId(),
                                node.messageId(),
                                node.messageText(),
                                node.text(),
                                node.title(),
                                node.detail(),
                                node.checklist(),
                                node.workType(),
                                node.nodeRole(),
                                person(node.marker()),
                                person(node.performer())))
                        .toList(),
                evidence.templates().stream()
                        .map(template -> new Template(
                                template.id(),
                                template.title(),
                                template.status(),
                                template.workType(),
                                template.responsibleRole(),
                                template.checklist()))
                        .toList(),
                evidence.jobs().stream()
                        .map(job -> new Job(job.id(), job.name(), job.status()))
                        .toList(),
                evidence.brackets().stream()
                        .map(bracket -> new Bracket(
                                bracket.id(),
                                bracket.jobId(),
                                bracket.jobName(),
                                bracket.workType(),
                                bracket.projectLabel(),
                                bracket.state(),
                                bracket.closeKind(),
                                bracket.outputValue(),
                                bracket.openedAt(),
                                bracket.closedAt(),
                                bracket.minutes()))
                        .toList(),
                evidence.waits().stream()
                        .map(wait -> new Wait(
                                wait.id(),
                                wait.bracketId(),
                                wait.jobId(),
                                wait.jobName(),
                                wait.kind(),
                                wait.reason(),
                                wait.openedAt(),
                                wait.satisfiedAt(),
                                wait.cancelledAt(),
                                wait.days()))
                        .toList(),
                new Spread(
                        evidence.spread().departments(),
                        evidence.spread().roles(),
                        evidence.spread().crossesDepartments()));
    }

    private static Person person(FindingEvidencePort.Person from) {
        return new Person(from.id(), from.name(), from.role(), from.department());
    }
}
