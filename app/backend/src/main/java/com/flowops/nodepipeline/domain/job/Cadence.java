package com.flowops.nodepipeline.domain.job;

import com.flowops.nodepipeline.domain.PipelineNode;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Cadence {
    private Cadence() {}

    public static Map<YearMonth, List<PipelineNode>> monthlyCycles(List<PipelineNode> nodes) {
        Map<YearMonth, List<PipelineNode>> cycles = new LinkedHashMap<>();
        nodes.stream()
                .sorted(java.util.Comparator.comparing(PipelineNode::createdAt))
                .forEach(node -> cycles.computeIfAbsent(
                                YearMonth.from(node.createdAt()), key -> new java.util.ArrayList<>())
                        .add(node));
        return cycles;
    }
}
