package com.flowops.nodepipeline.domain;

import java.util.List;
import java.util.UUID;

public record NodeVerdict(
        String nodeId,
        MatchTier tier,
        String why,
        String topTemplateId,
        Double score,
        Double confidence,
        Double separation,
        Double coverage,
        Double textScore,
        List<Finalist> finalists,
        UUID address,
        boolean shapeEvidence,

        /**
         * The tier a language model produced, when one was asked and said something the
         * deterministic layer acted on; null whenever no model spoke. Carried on the verdict
         * because a decision has to be explainable from its own row, and until this existed a run
         * could change a tier on a model's word and record nothing but the string
         * {@code ai_promoted}.
         */
        String aiOutcome,

        /** How sure the model said it was, on the same occasions. */
        Double aiConfidence) {
    public NodeVerdict {
        finalists = finalists == null ? List.of() : List.copyOf(finalists);
    }

    public record Finalist(String templateId, double score) {}

    public static NodeVerdict gated(PipelineNode node, String gates, UUID address) {
        return new NodeVerdict(
                node.id(),
                MatchTier.ABSTAIN,
                "gate:" + gates,
                null,
                null,
                null,
                null,
                null,
                null,
                List.of(),
                address,
                !node.disrupted(),
                null,
                null);
    }

    public boolean acts() {
        return tier != MatchTier.ABSTAIN;
    }
}
