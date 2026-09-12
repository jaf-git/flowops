package com.flowops.nodepipeline.domain.compose;

import java.util.List;

public record DraftProcess(
        String id,
        String name,
        String status,
        String origin,
        List<DraftStep> steps,
        List<DraftEdge> edges,
        Evidence evidence) {
    public DraftProcess {
        steps = steps == null ? List.of() : List.copyOf(steps);
        edges = edges == null ? List.of() : List.copyOf(edges);
    }

    public record DraftStep(
            String id,
            String label,
            String templateTitle,
            String taskTemplateId,
            String taskTemplateStatus,
            int lane,
            int position,
            boolean repeatable) {}

    public record DraftEdge(String dependentStepId, String dependsOnStepId, String kind, Double confidence) {
        public boolean blocks() {
            return "CONFIRMED".equals(kind);
        }
    }

    public record Evidence(
            List<String> jobIds, List<String> nodeIds, double certainty, Double orderConfidence, List<String> because) {
        public Evidence {
            jobIds = jobIds == null ? List.of() : List.copyOf(jobIds);
            nodeIds = nodeIds == null ? List.of() : List.copyOf(nodeIds);
            because = because == null ? List.of() : List.copyOf(because);
        }
    }

    public List<DraftEdge> blockingEdges() {
        return edges.stream().filter(DraftEdge::blocks).toList();
    }

    public List<DraftEdge> observedEdges() {
        return edges.stream().filter(edge -> !edge.blocks()).toList();
    }
}
