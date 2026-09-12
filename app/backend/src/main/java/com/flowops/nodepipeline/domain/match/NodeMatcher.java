package com.flowops.nodepipeline.domain.match;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.MatchTier;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.ai.SameWorkGate;
import com.flowops.shared.text.Intent;
import com.flowops.shared.text.Words;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;

public final class NodeMatcher {
    private final MatchWeights weights;
    private final Lexicons lexicons;

    private final BiFunction<PipelineNode, CandidateTemplate, Double> semantic;

    private final WorkJudge judge;

    public NodeMatcher(MatchWeights weights, Lexicons lexicons) {
        this(weights, lexicons, (node, template) -> null);
    }

    public NodeMatcher(MatchWeights weights, Lexicons lexicons, WorkJudge judge) {
        this(weights, lexicons, (node, template) -> null, judge);
    }

    public interface WorkJudge {
        boolean isEnabled(Judgement.PlugPoint plugPoint);

        Optional<Judgement.Verdict> judge(Judgement.Question question);
    }

    private static final WorkJudge NEVER_ASKED = new WorkJudge() {
        @Override
        public boolean isEnabled(Judgement.PlugPoint plugPoint) {
            return false;
        }

        @Override
        public Optional<Judgement.Verdict> judge(Judgement.Question question) {
            return Optional.empty();
        }
    };

    public NodeMatcher(
            MatchWeights weights, Lexicons lexicons, BiFunction<PipelineNode, CandidateTemplate, Double> semantic) {
        this(weights, lexicons, semantic, NEVER_ASKED);
    }

    public NodeMatcher(
            MatchWeights weights,
            Lexicons lexicons,
            BiFunction<PipelineNode, CandidateTemplate, Double> semantic,
            WorkJudge judge) {
        this.judge = judge;
        this.weights = weights;
        this.lexicons = lexicons;
        this.semantic = semantic;
    }

    public NodeVerdict match(PipelineNode node, List<CandidateTemplate> library) {
        MatchWeights.Gates gates = weights.gates();
        java.util.UUID address = gates.markerAddress() ? node.address() : node.creatorId();

        List<String> gated = gatesFor(node, gates);

        Optional<String> contradiction = gates.typeContradiction() && node.workType() != null
                ? lexicons.typeHintFrom(node.text(), node.detail()).filter(hint -> !hint.equals(node.workType()))
                : Optional.empty();

        if (!gated.isEmpty()) {
            return NodeVerdict.gated(node, String.join(",", gated), address);
        }

        List<CandidateTemplate> candidates =
                library.stream().filter(t -> t.eligibleFor(node.createdAt())).toList();
        if (candidates.isEmpty()) {
            return new NodeVerdict(
                    node.id(),
                    MatchTier.ABSTAIN,
                    "no_eligible_template",
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

        List<Scored> scored = new ArrayList<>();
        for (CandidateTemplate template : candidates) {
            scored.add(score(node, template, contradiction));
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed().thenComparing(s -> s.template()
                .id()));

        List<Scored> finalists = scored.subList(0, Math.min(weights.finalists(), scored.size()));
        Scored top = finalists.get(0);
        double runnerUp = finalists.size() > 1 ? finalists.get(1).score() : 0.0;
        double separation = top.score() - runnerUp;
        double confidence = confidenceOf(node, top, separation);

        return decide(node, top, separation, confidence, finalists, address);
    }

    private List<String> gatesFor(PipelineNode node, MatchWeights.Gates gates) {
        List<String> gated = new ArrayList<>();

        if (gates.boundary() && node.isBoundary()) {
            gated.add("job_boundary");
        }
        if (gates.closure() && node.diedRatherThanFinished()) {
            gated.add(node.closure().name().toLowerCase(java.util.Locale.ROOT));
        }
        if (Words.normalise(node.text()).isEmpty()) {
            gated.add("no_text");
        }
        if (node.workType() == null) {
            gated.add("no_work_type");
        }
        if (gated.isEmpty()
                && Words.quality(node.text(), weights.plausibleFloor(), weights.uniqueFloor())
                        < weights.textQualityFloor()) {
            gated.add("text_no_signal");
        }
        if (gates.direction() && node.isConversation()) {
            gated.add("direction:" + node.direction().toLowerCase(java.util.Locale.ROOT));
        }
        if (gated.isEmpty() && gates.intent()) {
            Intent intent = Intent.of(node.text(), node.detail());
            if (intent != Intent.WORK) {
                gated.add("intent:" + intent.name().toLowerCase(java.util.Locale.ROOT));
            }
        }
        if (gates.postClose() && node.postClose()) {
            gated.add("post_close");
        }
        return gated;
    }

    private Scored score(PipelineNode node, CandidateTemplate template, Optional<String> contradiction) {
        Double text = Affinity.text(node, template, weights, semantic.apply(node, template));
        Double role = Affinity.role(node, template, lexicons);

        if (node.disrupted() && node.workType() != null) {
            double inherited = node.workType().equals(template.workType()) ? 1.0 : 0.0;
            role = Math.max(role == null ? 0.0 : role, inherited);
        }

        if (contradiction.isPresent()
                && contradiction.get().equals(template.workType())
                && text != null
                && text >= 0.75) {
            role = 1.0;
        }

        Double shape = weights.gates().shapeFeature() ? Affinity.structure(node, template, weights) : null;
        Double output = Affinity.output(node, template, weights);

        double weighted = 0.0;
        double present = 0.0;
        if (text != null) {
            weighted += weights.textWeight() * text;
            present += weights.textWeight();
        }
        if (role != null) {
            weighted += weights.roleWeight() * role;
            present += weights.roleWeight();
        }
        if (shape != null) {
            weighted += weights.shapeWeight() * shape;
            present += weights.shapeWeight();
        }
        if (output != null) {
            weighted += weights.outputWeight() * output;
            present += weights.outputWeight();
        }

        return new Scored(
                template,
                present == 0 ? 0.0 : weighted / present,
                text == null ? 0.0 : text,
                present / weights.totalWeight());
    }

    private double confidenceOf(PipelineNode node, Scored top, double separation) {
        return 0.30 * top.coverage()
                + 0.25 * Words.quality(node.text(), weights.plausibleFloor(), weights.uniqueFloor())
                + 0.25 * Math.min(1.0, separation / 0.15)
                + 0.20 * (top.text() >= 0.6 ? 1.0 : 0.4);
    }

    private NodeVerdict decide(
            PipelineNode node,
            Scored top,
            double separation,
            double confidence,
            List<Scored> finalists,
            java.util.UUID address) {
        List<NodeVerdict.Finalist> carried = finalists.stream()
                .map(f -> new NodeVerdict.Finalist(f.template().id(), round(f.score())))
                .toList();

        MatchTier tier;
        String why;

        boolean deterministicWasBelowFloor = false;

        if (top.text() < weights.textVeto()) {
            tier = MatchTier.ABSTAIN;
            why = "veto:text_floor";
        } else if (top.score() < weights.scoreFloor()) {
            tier = MatchTier.ABSTAIN;
            why = "below_floor";
            deterministicWasBelowFloor = true;
        } else if (confidence < weights.confidenceFloor()) {
            tier = MatchTier.ABSTAIN;
            why = "low_confidence";
        } else if (separation < weights.separationMinimum()) {
            tier = MatchTier.ADJUST;
            why = "near_tie";
        } else if (top.template().id().equals(node.taskTemplateId())) {
            tier = MatchTier.OK;
            why = "ran_template";
        } else if (node.taskTemplateId() != null) {
            tier = MatchTier.ADJUST;
            why = "ran_other_template";
        } else if (node.performerRole() != null
                && top.template().responsibleRole() != null
                && !node.performerRole().equals(top.template().responsibleRole())
                && top.text() >= 0.75
                && top.score() >= 0.75) {
            tier = MatchTier.ROLE_MISMATCH;
            why = "outside this person's role";
        } else {
            tier = MatchTier.NUDGE;
            why = "matched_ad_hoc";
        }

        String aiOutcome = null;
        Double aiConfidence = null;

        Judgement.Verdict opinion = null;
        if (judge.isEnabled(Judgement.PlugPoint.SAME_WORK)
                && SameWorkGate.inUncertaintyBand(top.text(), top.score(), weights.textVeto(), weights.scoreFloor())) {
            opinion = judge.judge(new Judgement.Question(
                            Judgement.PlugPoint.SAME_WORK,
                            node.text(),
                            List.of(top.template().id()),
                            top.template().title()))
                    .orElse(null);

            MatchTier afterTheModel =
                    SameWorkGate.decide(tier, opinion, deterministicWasBelowFloor, weights.confidenceFloor());
            if (afterTheModel != tier) {
                why = tier == MatchTier.ABSTAIN ? "ai_promoted" : "ai_vetoed";
                tier = afterTheModel;

                // Only when the model actually moved the tier, which is the same occasion
                // COMPARE fills these columns on. One meaning for the column on both paths
                // matters more than recording an opinion nobody acted on.
                aiOutcome = afterTheModel.name();
                aiConfidence = opinion == null ? null : opinion.confidence();
            }
        }

        return new NodeVerdict(
                node.id(),
                tier,
                why,
                top.template().id(),
                round(top.score()),
                round(confidence),
                round(separation),
                round2(top.coverage()),
                round2(top.text()),
                carried,
                address,
                !node.disrupted(),
                aiOutcome,
                aiConfidence);
    }

    private static double round(double value) {
        return rounded(value, 3);
    }

    private static double round2(double value) {
        return rounded(value, 2);
    }

    private static double rounded(double value, int places) {
        return new java.math.BigDecimal(value)
                .setScale(places, java.math.RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private record Scored(CandidateTemplate template, double score, double text, double coverage) {}
}
