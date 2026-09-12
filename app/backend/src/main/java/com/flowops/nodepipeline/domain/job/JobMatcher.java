package com.flowops.nodepipeline.domain.job;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.NodeVerdict;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.match.NodeMatcher;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class JobMatcher {
    private final NodeMatcher nodes;
    private final JobWeights weights;
    private final LocalDate today;

    public JobMatcher(NodeMatcher nodes, JobWeights weights, LocalDate today) {
        this.nodes = nodes;
        this.weights = weights;
        this.today = today;
    }

    public JobVerdict match(
            PipelineJob job,
            List<PipelineNode> jobNodes,
            List<CandidateTemplate> library,
            List<ProcessShape> processes,
            boolean asCycle) {
        List<NodeVerdict> verdicts = new ArrayList<>(jobNodes.size());
        List<String> matched = new ArrayList<>();
        for (PipelineNode node : jobNodes) {
            NodeVerdict verdict = nodes.match(node, library);
            verdicts.add(verdict);
            if (verdict.acts()) {
                matched.add(verdict.topTemplateId());
            }
        }

        List<Fit> ranked = rank(matched, processes);

        boolean hasEnd = jobNodes.stream().anyMatch(n -> "JOB_END".equals(n.kind()));
        if (!job.isFinished(hasEnd)) {
            long idle = ChronoUnit.DAYS.between(job.lastActivityAt(), today);
            if (idle >= weights.staleDays()) {
                return verdict(JobTier.STALE, "open, nothing for " + idle + " days", job, verdicts, ranked, matched);
            }
            return verdict(
                    JobTier.IN_PROGRESS,
                    "still running, " + idle + "d since last activity",
                    job,
                    verdicts,
                    ranked,
                    matched);
        }

        JobTier gated = columnGate(job, jobNodes.size(), asCycle);
        if (gated != null) {
            return verdict(gated, gateReason(gated, job, jobNodes.size()), job, verdicts, ranked, matched);
        }

        if (ranked.isEmpty()) {
            return verdict(JobTier.UNKNOWN_PATTERN, "no process templates exist yet", job, verdicts, ranked, matched);
        }

        return decide(job, verdicts, ranked, matched);
    }

    private List<Fit> rank(List<String> matched, List<ProcessShape> processes) {
        Set<String> got = new LinkedHashSet<>(matched);
        List<Fit> fits = new ArrayList<>();

        for (ProcessShape process : processes) {
            List<String> need = process.steps();
            List<String> have = need.stream().filter(got::contains).toList();

            double cover = need.isEmpty() ? 0.0 : (double) have.size() / need.size();
            long inside = matched.stream().filter(need::contains).count();
            double explained = matched.isEmpty() ? 0.0 : (double) inside / matched.size();
            double extra = matched.isEmpty() ? 0.0 : (double) (matched.size() - inside) / matched.size();

            double score = weights.coverWeight() * cover
                    + weights.explainedWeight() * explained
                    + weights.extraWeight() * (1 - extra);

            fits.add(new Fit(process, cover, explained, extra, have, score));
        }

        fits.sort(Comparator.comparingDouble(Fit::score).reversed().thenComparing(f -> f.process()
                .id()));
        return fits;
    }

    private JobTier columnGate(PipelineJob job, int nodeCount, boolean asCycle) {
        if (!job.shapeEligible()) {
            return JobTier.NOT_SHAPE_EVIDENCE;
        }
        if (job.rework()) {
            return JobTier.REWORK;
        }
        if (nodeCount < weights.minimumJobNodes()) {
            return JobTier.NOT_A_JOB;
        }
        if (job.standing() && !asCycle) {
            return JobTier.STANDING;
        }
        return null;
    }

    private static String gateReason(JobTier tier, PipelineJob job, int nodeCount) {
        return switch (tier) {
            case NOT_SHAPE_EVIDENCE -> "shape_eligible=false";
            case REWORK -> "rework of " + job.reworkOfJobId();
            case NOT_A_JOB -> "too few nodes";
            case STANDING -> "retainer - matches per cadence, not per job";
            default -> tier.name();
        };
    }

    private JobVerdict decide(PipelineJob job, List<NodeVerdict> verdicts, List<Fit> ranked, List<String> matched) {
        Fit best = ranked.get(0);
        double runnerUp = ranked.size() > 1 ? ranked.get(1).score() : 0.0;
        double separation = best.score() - runnerUp;
        boolean fellThrough = false;

        if (weights.fallthrough() && best.cover() < weights.coverMinimum()) {
            List<Fit> alternatives = ranked.subList(1, ranked.size()).stream()
                    .filter(f -> f.cover() >= weights.coverMinimum() && f.extra() <= weights.extraMaximum())
                    .toList();
            if (!alternatives.isEmpty()) {
                best = alternatives.get(0);
                fellThrough = true;
                double next = alternatives.size() > 1
                        ? alternatives.subList(1, alternatives.size()).stream()
                                .mapToDouble(Fit::score)
                                .max()
                                .orElse(0.0)
                        : 0.0;
                separation = best.score() - next;
            }
        }

        Set<String> got = new LinkedHashSet<>(matched);

        if (best.cover() < weights.coverMinimum()) {
            List<String> need = best.process().steps();
            int firstGap = need.size();
            for (int i = 0; i < need.size(); i++) {
                if (!got.contains(need.get(i))) {
                    firstGap = i;
                    break;
                }
            }
            int gap = firstGap;
            boolean formsPrefix = best.have().stream().allMatch(step -> need.indexOf(step) < gap);

            if (weights.prefixStall() && best.have().size() >= 2 && formsPrefix) {
                return verdict(
                        JobTier.PARTIAL_RUN,
                        best.have().size() + "/" + need.size() + " steps",
                        job,
                        verdicts,
                        ranked,
                        matched,
                        best,
                        separation,
                        fellThrough);
            }
            return verdict(
                    JobTier.UNKNOWN_PATTERN,
                    "cover %.2f".formatted(best.cover()),
                    job,
                    verdicts,
                    ranked,
                    matched,
                    best,
                    separation,
                    fellThrough);
        }

        if (best.extra() > weights.extraMaximum()) {
            return verdict(
                    JobTier.MIXED, "too much outside", job, verdicts, ranked, matched, best, separation, fellThrough);
        }

        if (separation < weights.separationMinimum()) {
            Fit runner = ranked.size() > 1 ? ranked.get(1) : null;
            boolean dominant = weights.explanatoryTiebreak()
                    && runner != null
                    && best.explained() >= 1.0
                    && runner.explained() < 1.0;
            if (!dominant) {
                return verdict(
                        JobTier.AMBIGUOUS,
                        "sep %.2f".formatted(separation),
                        job,
                        verdicts,
                        ranked,
                        matched,
                        best,
                        separation,
                        fellThrough);
            }
        }

        return verdict(JobTier.PROCESS_RUN, "matched", job, verdicts, ranked, matched, best, separation, fellThrough);
    }

    private JobVerdict verdict(
            JobTier tier,
            String why,
            PipelineJob job,
            List<NodeVerdict> verdicts,
            List<Fit> ranked,
            List<String> matched) {
        return verdict(tier, why, job, verdicts, ranked, matched, ranked.isEmpty() ? null : ranked.get(0), 0.0, false);
    }

    private JobVerdict verdict(
            JobTier tier,
            String why,
            PipelineJob job,
            List<NodeVerdict> verdicts,
            List<Fit> ranked,
            List<String> matched,
            Fit best,
            double separation,
            boolean fellThrough) {
        Set<String> got = new LinkedHashSet<>(matched);
        List<String> missing = best == null
                ? List.of()
                : best.process().steps().stream().filter(s -> !got.contains(s)).toList();

        List<String> unexplained = best == null
                ? List.of()
                : matched.stream()
                        .filter(t -> !best.process().steps().contains(t))
                        .distinct()
                        .toList();

        Map<String, Integer> repeated = new LinkedHashMap<>();
        for (String template : matched) {
            repeated.merge(template, 1, Integer::sum);
        }
        repeated.values().removeIf(count -> count < 2);

        return new JobVerdict(
                job.id(),
                tier,
                why,
                best == null ? null : best.process().id(),
                best == null ? 0.0 : round(best.score()),
                best == null ? 0.0 : round(best.cover()),
                best == null ? 0.0 : round(best.explained()),
                best == null ? 0.0 : round(best.extra()),
                round(separation),
                job.scope(),
                missing,
                unexplained,
                Map.copyOf(repeated),
                fellThrough,
                verdicts,
                ranked.stream()
                        .limit(3)
                        .map(f -> new JobVerdict.Ranked(f.process().id(), round(f.score()), round(f.cover())))
                        .toList());
    }

    private static double round(double value) {
        return new java.math.BigDecimal(value)
                .setScale(3, java.math.RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private record Fit(
            ProcessShape process, double cover, double explained, double extra, List<String> have, double score) {}
}
