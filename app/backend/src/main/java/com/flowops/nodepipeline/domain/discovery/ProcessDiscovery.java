package com.flowops.nodepipeline.domain.discovery;

import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.job.PipelineJob;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class ProcessDiscovery {
    /**
     * The order the marks were actually made in.
     *
     * <p>The instant first, because it is the only field that separates two marks made on one day —
     * and a corpus loaded in one afternoon is entirely made of those. Where it is absent the date
     * decides, and where the date ties too the identifier does, which is arbitrary but stable: a
     * sequence that changed between two runs of the same data would make every order figure
     * unreproducible.
     */
    private static final Comparator<PipelineNode> IN_THE_ORDER_THEY_HAPPENED = Comparator.comparing(
                    PipelineNode::markedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(PipelineNode::createdAt)
            .thenComparing(PipelineNode::id);

    private final DiscoveryWeights weights;

    public ProcessDiscovery(DiscoveryWeights weights) {
        this.weights = weights;
    }

    public List<DiscoveredProcess> discover(List<PipelineJob> jobs, List<PipelineNode> nodes, List<StepKind> kinds) {
        Map<String, String> kindOfNode = new LinkedHashMap<>();
        for (StepKind kind : kinds) {
            kind.nodeIds().forEach(nodeId -> kindOfNode.put(nodeId, kind.id()));
        }

        Map<String, PipelineJob> byId = new LinkedHashMap<>();
        jobs.forEach(job -> byId.put(job.id(), job));

        Map<String, Boolean> hasEnd = new LinkedHashMap<>();
        nodes.forEach(node -> hasEnd.merge(node.jobId(), "JOB_END".equals(node.kind()), (a, b) -> a || b));

        Map<String, List<PipelineNode>> byJob = new LinkedHashMap<>();
        for (PipelineNode node : nodes) {
            if (kindOfNode.containsKey(node.id())) {
                byJob.computeIfAbsent(node.jobId(), key -> new ArrayList<>()).add(node);
            }
        }

        List<Candidate> candidates = new ArrayList<>();
        for (Map.Entry<String, List<PipelineNode>> entry : byJob.entrySet()) {
            PipelineJob job = byId.get(entry.getKey());
            if (job == null || !job.shapeEligible() || job.rework()) {
                continue;
            }
            if (!job.isFinished(hasEnd.getOrDefault(job.id(), false))) {
                continue;
            }

            List<String> sequence = entry.getValue().stream()
                    .sorted(IN_THE_ORDER_THEY_HAPPENED)
                    .map(node -> kindOfNode.get(node.id()))
                    .toList();

            candidates.add(new Candidate(job, new LinkedHashSet<>(sequence), sequence));
        }

        candidates.sort(Comparator.comparingInt((Candidate c) -> c.steps().size())
                .reversed()
                .thenComparing(c -> c.job().id()));

        List<Cluster> clusters = new ArrayList<>();
        for (Candidate candidate : candidates) {
            boolean placed = false;
            for (Cluster cluster : clusters) {
                Set<String> shared = new LinkedHashSet<>(candidate.steps());
                shared.retainAll(cluster.core);

                int missing = cluster.core.size() - shared.size();
                int foreign = candidate.steps().size() - shared.size();

                if (missing <= weights.stepTolerance() && foreign <= weights.stepTolerance()) {
                    cluster.members.add(candidate);
                    cluster.core = majorityCore(cluster.members);
                    placed = true;
                    break;
                }
            }
            if (!placed) {
                Cluster fresh = new Cluster();
                fresh.members.add(candidate);
                fresh.core = new LinkedHashSet<>(candidate.steps());
                clusters.add(fresh);
            }
        }

        List<DiscoveredProcess> found = new ArrayList<>();
        for (Cluster cluster : clusters) {
            if (cluster.members.size() < weights.minimumRunsPerProcess()) {
                continue;
            }

            if (cluster.core.size() < 2) {
                continue;
            }
            found.add(processOf(cluster));
        }
        found.sort(Comparator.comparingDouble(DiscoveredProcess::certainty).reversed());
        return found;
    }

    private static Set<String> majorityCore(List<Candidate> members) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        members.forEach(member -> member.steps().forEach(step -> counts.merge(step, 1, Integer::sum)));

        int needed = Math.max(1, members.size() / 2);
        Set<String> core = counts.entrySet().stream()
                .filter(e -> e.getValue() >= needed)
                .map(Map.Entry::getKey)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));

        if (core.isEmpty()) {
            members.forEach(member -> core.addAll(member.steps()));
        }
        return core;
    }

    private DiscoveredProcess processOf(Cluster cluster) {
        Map<String, List<Integer>> positions = new LinkedHashMap<>();
        for (Candidate member : cluster.members) {
            List<String> sequence = member.sequence();
            for (int i = 0; i < sequence.size(); i++) {
                if (cluster.core.contains(sequence.get(i))) {
                    positions
                            .computeIfAbsent(sequence.get(i), key -> new ArrayList<>())
                            .add(i);
                }
            }
        }

        List<String> order = cluster.core.stream()
                .sorted(Comparator.comparingInt(step -> median(positions.getOrDefault(step, List.of(0)))))
                .toList();

        int agree = 0;
        int total = 0;
        for (Candidate member : cluster.members) {
            List<String> inCore =
                    member.sequence().stream().filter(cluster.core::contains).toList();
            for (int a = 0; a < inCore.size(); a++) {
                for (int b = a + 1; b < inCore.size(); b++) {
                    total++;
                    if (order.indexOf(inCore.get(a)) <= order.indexOf(inCore.get(b))) {
                        agree++;
                    }
                }
            }
        }
        double orderConfidence = total == 0 ? 0.0 : (double) agree / total;

        int runs = cluster.members.size();
        boolean enoughRuns = runs >= weights.orderMinimumRuns();
        boolean enoughAgreement = orderConfidence >= weights.orderConfidenceFloor();
        boolean reliable = enoughRuns && enoughAgreement;

        double certainty =
                Math.min(1.0, (runs / 6.0) * 0.5 + orderConfidence * 0.3 + (cluster.core.size() / 6.0) * 0.2);

        return new DiscoveredProcess(
                order,
                cluster.members.stream().map(m -> m.job().id()).toList(),
                runs,
                round(orderConfidence),
                reliable,
                round(certainty),
                cluster.members.stream()
                        .map(m -> m.sequence().stream()
                                .filter(cluster.core::contains)
                                .toList())
                        .toList(),
                orderWithheldBecause(enoughRuns, enoughAgreement));
    }

    private String orderWithheldBecause(boolean enoughRuns, boolean enoughAgreement) {
        if (enoughRuns && enoughAgreement) {
            return null;
        }

        StringBuilder why = new StringBuilder("order not shown, ");
        if (!enoughRuns) {
            why.append("under ").append(weights.orderMinimumRuns()).append(" runs");
        }
        if (!enoughAgreement) {
            why.append(enoughRuns ? "" : " and ")
                    .append("agreement under %.0f%%".formatted(weights.orderConfidenceFloor() * 100));
        }
        return why.toString();
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = values.stream().sorted().toList();
        return sorted.get(sorted.size() / 2);
    }

    private static double round(double value) {
        return new java.math.BigDecimal(value)
                .setScale(3, java.math.RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private record Candidate(PipelineJob job, Set<String> steps, List<String> sequence) {}

    private static final class Cluster {
        private final List<Candidate> members = new ArrayList<>();
        private Set<String> core = new LinkedHashSet<>();
    }
}
