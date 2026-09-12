package com.flowops.nodepipeline.domain.job;

import com.flowops.nodepipeline.domain.NodeVerdict;
import java.util.List;
import java.util.Map;

public record JobVerdict(
        String jobId,
        JobTier tier,
        String why,
        String processId,
        double score,
        double cover,
        double explained,
        double extra,
        double separation,
        String scope,
        List<String> missing,
        List<String> unexplained,
        Map<String, Integer> repeatedSteps,
        boolean fellThrough,
        List<NodeVerdict> nodes,
        List<Ranked> ranked) {
    public JobVerdict {
        missing = missing == null ? List.of() : List.copyOf(missing);
        unexplained = unexplained == null ? List.of() : List.copyOf(unexplained);
        repeatedSteps = repeatedSteps == null ? Map.of() : Map.copyOf(repeatedSteps);
        nodes = nodes == null ? List.of() : List.copyOf(nodes);
        ranked = ranked == null ? List.of() : List.copyOf(ranked);
    }

    public record Ranked(String processId, double score, double cover) {}

    public boolean isDiscoveryCandidate() {
        return tier == JobTier.UNKNOWN_PATTERN;
    }
}
