package com.flowops.nodepipeline.domain.discovery;

import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.job.PipelineJob;
import com.flowops.shared.text.Words;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Churn {
    private static final int REPEATS_THAT_MEAN_CHURN = 3;

    private static final int COMPARED_PREFIX = 24;

    private Churn() {}

    public static Set<String> excludedFrom(List<PipelineNode> nodes, List<PipelineJob> jobs) {
        Set<String> excluded = new LinkedHashSet<>();
        excluded.addAll(fromUnfinishedJobs(nodes, jobs));
        excluded.addAll(repeatedMarks(nodes, jobs));
        return excluded;
    }

    public static Set<String> fromUnfinishedJobs(List<PipelineNode> nodes, List<PipelineJob> jobs) {
        Map<String, Boolean> hasEnd = new LinkedHashMap<>();
        nodes.forEach(node -> hasEnd.merge(node.jobId(), "JOB_END".equals(node.kind()), (a, b) -> a || b));

        Set<String> finished = new LinkedHashSet<>();
        jobs.stream()
                .filter(job -> job.isFinished(hasEnd.getOrDefault(job.id(), false)))
                .forEach(job -> finished.add(job.id()));

        return nodes.stream()
                .filter(node -> !finished.contains(node.jobId()))
                .map(PipelineNode::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    public static Set<String> repeatedMarks(List<PipelineNode> nodes, List<PipelineJob> jobs) {
        Set<String> standing = new LinkedHashSet<>();
        jobs.stream().filter(PipelineJob::standing).forEach(job -> standing.add(job.id()));

        Map<String, List<PipelineNode>> byRepeat = new LinkedHashMap<>();
        for (PipelineNode node : nodes) {
            if (standing.contains(node.jobId())) {
                continue;
            }
            String normalised = Words.normalise(node.text());
            String key = node.address() + "|" + node.jobId() + "|"
                    + normalised.substring(0, Math.min(COMPARED_PREFIX, normalised.length())) + "|"
                    + (node.namesAnActivity() ? node.activitySlug() : "");
            byRepeat.computeIfAbsent(key, k -> new java.util.ArrayList<>()).add(node);
        }

        Set<String> churned = new LinkedHashSet<>();
        byRepeat.values().stream()
                .filter(group -> group.size() >= REPEATS_THAT_MEAN_CHURN)
                .forEach(group -> group.forEach(node -> churned.add(node.id())));
        return churned;
    }
}
