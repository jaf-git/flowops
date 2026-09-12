package com.flowops.nodepipeline.domain.match;

import com.flowops.nodepipeline.domain.CandidateTemplate;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.shared.text.Words;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Affinity {
    private static final Map<String, String> OUTPUT_VOCABULARY = Map.of(
            "TEXT", "TEXT",
            "DESIGN", "DESIGN",
            "REPORT", "REPORT",
            "SCHEDULING", "SCHEDULE",
            "NONE", "NONE");

    private static final Set<String> UNEXPRESSIBLE = Set.of("DECISION", "PHYSICAL");

    private Affinity() {}

    public static Double text(PipelineNode node, CandidateTemplate template, MatchWeights weights, Double semantic) {
        String body = node.text() + " " + (node.title() == null ? "" : node.title()) + " "
                + (node.detail() == null ? "" : node.detail());

        if (weights.gates().keywords()) {
            List<String> hits = template.keywords().stream()
                    .filter(keyword -> keywordPresent(keyword, body))
                    .toList();
            if (!hits.isEmpty()) {
                boolean anyPhrase = hits.stream().anyMatch(k -> Words.tokens(k).size() > 1);
                double corroboration =
                        Math.max(agreement(node, template.title()), agreement(node, template.description()));
                if (anyPhrase || corroboration >= weights.keywordCorroboration()) {
                    return 0.90;
                }
                return Math.min(0.72, 0.55 + corroboration);
            }
        }

        double base = agreement(node, template.title());
        if (template.description() != null) {
            base = Math.max(base, agreement(node, template.description()) * 0.8);
        }
        for (String line : template.checklist()) {
            base = Math.max(base, agreement(node, line) * 0.85);
        }
        base = Math.min(1.0, base * 1.5);

        if (semantic != null) {
            base = Math.max(base, semantic * 0.85);
        }
        return base;
    }

    public static Double role(PipelineNode node, CandidateTemplate template, Lexicons lexicons) {
        if (node.performerRole() == null || template.responsibleRole() == null) {
            return null;
        }
        return lexicons.kinship(node.performerRole(), template.responsibleRole());
    }

    public static Double structure(PipelineNode node, CandidateTemplate template, MatchWeights weights) {
        List<Double> parts = new ArrayList<>();

        String effective = node.disrupted() ? node.workType() : firstNonNull(node.performerRole(), node.workType());
        if (effective != null && template.responsibleRole() != null) {
            parts.add(
                    effective.equals(template.responsibleRole()) || effective.equals(template.workType()) ? 1.0 : 0.0);
        }
        if (node.precedingRole() != null && template.precedingRole() != null) {
            parts.add(node.precedingRole().equals(template.precedingRole()) ? 1.0 : 0.0);
        }
        if (node.followingRole() != null && template.followingRole() != null) {
            parts.add(node.followingRole().equals(template.followingRole()) ? 1.0 : 0.0);
        }
        if (node.positionInTrack() != null && template.position() != null) {
            parts.add(Math.max(0.0, 1.0 - Math.abs(node.positionInTrack() - template.position()) / 3.0));
        }
        Double produced = output(node, template, weights);
        if (produced != null) {
            parts.add(produced);
        }

        if (parts.isEmpty()) {
            return null;
        }
        return parts.stream().mapToDouble(Double::doubleValue).average().orElseThrow();
    }

    public static Double output(PipelineNode node, CandidateTemplate template, MatchWeights weights) {
        if (!weights.gates().outputMap() || node.outputType() == null || template.outputKind() == null) {
            return null;
        }
        if ("NONE".equals(node.outputType()) || UNEXPRESSIBLE.contains(template.outputKind())) {
            return null;
        }
        String mapped = OUTPUT_VOCABULARY.get(node.outputType());
        if (mapped == null) {
            return null;
        }
        return mapped.equals(template.outputKind()) ? 1.0 : 0.0;
    }

    private static double agreement(PipelineNode node, String templateWords) {
        double said = Words.trigramOverlap(node.text(), templateWords);
        if (!node.isDescribed()) {
            return said;
        }
        return Math.max(said, Words.trigramOverlap(node.title(), templateWords));
    }

    private static boolean keywordPresent(String keyword, String body) {
        List<String> needed = Words.tokens(keyword);
        if (needed.isEmpty()) {
            return false;
        }
        List<String> present = Words.tokens(body);
        return needed.stream()
                .allMatch(need -> present.stream().anyMatch(word -> word.startsWith(need) || need.startsWith(word)));
    }

    private static String firstNonNull(String one, String other) {
        return one != null ? one : other;
    }
}
