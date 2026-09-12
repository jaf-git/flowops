package com.flowops.analyser.domain;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

public record FindingContext(
        List<String> clients,
        List<String> projects,
        int engagements,
        List<String> workTypes,
        Instant from,
        Instant to) {
    public FindingContext {
        clients = clients == null ? List.of() : List.copyOf(clients);
        projects = projects == null ? List.of() : List.copyOf(projects);
        workTypes = workTypes == null ? List.of() : List.copyOf(workTypes);
    }

    public boolean isEmpty() {
        return clients.isEmpty() && projects.isEmpty() && engagements == 0 && workTypes.isEmpty() && from == null;
    }

    public static FindingContext of(Finding finding, Snapshot snapshot, Map<String, String> names) {
        Set<String> jobIds = new LinkedHashSet<>(finding.evidence().getOrDefault(Finding.EvidenceKind.JOB, List.of()));
        Set<String> nodeIds =
                new LinkedHashSet<>(finding.evidence().getOrDefault(Finding.EvidenceKind.NODE, List.of()));

        List<Snapshot.Node> nodes = snapshot.nodes().stream()
                .filter(node -> nodeIds.contains(node.id()))
                .toList();

        Set<String> allJobIds = new LinkedHashSet<>(jobIds);
        if (allJobIds.isEmpty()) {
            nodes.stream()
                    .map(Snapshot.Node::jobId)
                    .filter(java.util.Objects::nonNull)
                    .forEach(allJobIds::add);
        }

        List<Snapshot.Job> jobs = snapshot.jobs().stream()
                .filter(job -> allJobIds.contains(job.id()))
                .toList();

        return new FindingContext(
                distinct(jobs, Snapshot.Job::counterpartyName),
                distinct(jobs, Snapshot.Job::projectLabel),
                allJobIds.size(),
                nodes.stream()
                        .map(Snapshot.Node::workType)
                        .filter(FindingContext::present)
                        .map(workType -> names.getOrDefault(workType, workType))
                        .distinct()
                        .sorted()
                        .toList(),
                span(nodes, jobs, true),
                span(nodes, jobs, false));
    }

    private static List<String> distinct(List<Snapshot.Job> jobs, Function<Snapshot.Job, String> field) {
        return jobs.stream()
                .map(field)
                .filter(FindingContext::present)
                .distinct()
                .sorted()
                .collect(Collectors.toList());
    }

    private static Instant span(List<Snapshot.Node> nodes, List<Snapshot.Job> jobs, boolean earliest) {
        java.util.stream.Stream<Instant> moments = java.util.stream.Stream.concat(
                        nodes.stream().map(Snapshot.Node::createdAt),
                        jobs.stream().map(Snapshot.Job::openedAt))
                .filter(java.util.Objects::nonNull);

        return (earliest ? moments.min(Instant::compareTo) : moments.max(Instant::compareTo)).orElse(null);
    }

    private static boolean present(String value) {
        return value != null && !value.isBlank();
    }
}
