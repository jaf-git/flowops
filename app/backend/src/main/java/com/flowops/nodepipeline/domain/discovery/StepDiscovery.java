package com.flowops.nodepipeline.domain.discovery;

import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.ConceptSplit;
import com.flowops.shared.text.Intent;
import com.flowops.shared.text.Words;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class StepDiscovery {
    private final DiscoveryWeights weights;
    private final Set<String> directConversations;

    public StepDiscovery(DiscoveryWeights weights) {
        this(weights, Set.of());
    }

    public StepDiscovery(DiscoveryWeights weights, Set<String> directConversations) {
        this.weights = weights;
        this.directConversations = Set.copyOf(directConversations);
    }

    @FunctionalInterface
    public interface ConceptReader {
        String conceptOf(PipelineNode node);
    }

    public List<StepKind> discover(List<PipelineNode> nodes, Set<String> excluded) {
        return kindsFrom(bucketsOf(nodes, excluded));
    }

    public List<StepKind> refinedForDrafting(List<PipelineNode> nodes, Set<String> excluded, ConceptReader concepts) {
        return kindsFrom(splitByConcept(bucketsOf(nodes, excluded), concepts));
    }

    private Map<Signature, List<PipelineNode>> bucketsOf(List<PipelineNode> nodes, Set<String> excluded) {
        List<PipelineNode> pool = nodes.stream()
                .filter(node -> !excluded.contains(node.id()))
                .filter(this::eligible)
                .toList();

        Map<Signature, List<PipelineNode>> buckets = new LinkedHashMap<>();
        for (PipelineNode node : pool) {
            buckets.computeIfAbsent(signatureOf(node), key -> new ArrayList<>()).add(node);
        }

        return weights.conversationSplit() ? splitByConversation(buckets) : buckets;
    }

    private List<StepKind> kindsFrom(Map<Signature, List<PipelineNode>> buckets) {
        List<StepKind> kinds = new ArrayList<>();
        for (Map.Entry<Signature, List<PipelineNode>> bucket : buckets.entrySet()) {
            List<PipelineNode> group = bucket.getValue();
            if (group.size() < weights.minimumNodesPerKind()) {
                continue;
            }
            kinds.add(kindOf(bucket.getKey(), group));
        }
        return kinds;
    }

    public static final String NO_GOVERNED_WORK_TYPE = "no_governed_work_type";

    public Set<String> ungoverned(List<PipelineNode> nodes, Set<String> excluded) {
        return nodes.stream()
                .filter(node -> !excluded.contains(node.id()))
                .filter(node -> node.workType() == null)
                .filter(node -> !node.isBoundary() && !node.diedRatherThanFinished() && !node.isConversation())
                .map(PipelineNode::id)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    private boolean eligible(PipelineNode node) {
        if (node.isBoundary() || node.diedRatherThanFinished() || node.isConversation()) {
            return false;
        }

        if (node.workType() == null) {
            return false;
        }

        if (!node.isDescribed()) {
            if (Words.normalise(node.text()).isEmpty()) {
                return false;
            }
            if (Words.quality(node.text(), weights.plausibleFloor(), weights.uniqueFloor())
                    < weights.textQualityFloor()) {
                return false;
            }
        }
        return Intent.of(node.text(), node.detail()) == Intent.WORK;
    }

    private static Signature signatureOf(PipelineNode node) {
        return new Signature(node.workType(), node.activitySlug(), null, null, null);
    }

    private Map<Signature, List<PipelineNode>> splitByConversation(Map<Signature, List<PipelineNode>> buckets) {
        Map<Signature, List<PipelineNode>> refined = new LinkedHashMap<>();

        for (Map.Entry<Signature, List<PipelineNode>> bucket : buckets.entrySet()) {
            Signature signature = bucket.getKey();
            List<PipelineNode> group = bucket.getValue();

            if (signature.activity() != null) {
                refined.put(signature, group);
                continue;
            }

            Map<String, List<PipelineNode>> byConversation = new LinkedHashMap<>();
            for (PipelineNode node : group) {
                byConversation
                        .computeIfAbsent(node.conversationId(), key -> new ArrayList<>())
                        .add(node);
            }

            List<String> directMessages = byConversation.entrySet().stream()
                    .filter(e -> e.getKey() != null
                            && directConversations.contains(e.getKey())
                            && e.getValue().size() >= weights.minimumNodesPerKind())
                    .map(Map.Entry::getKey)
                    .toList();

            int outside = byConversation.entrySet().stream()
                    .filter(e -> !directMessages.contains(e.getKey()))
                    .mapToInt(e -> e.getValue().size())
                    .sum();

            if (!directMessages.isEmpty() && outside >= weights.minimumNodesPerKind()) {
                for (String conversation : directMessages) {
                    refined.put(
                            new Signature(
                                    signature.workType(),
                                    signature.activity(),
                                    signature.outputType(),
                                    conversation,
                                    null),
                            byConversation.get(conversation));
                }
                List<PipelineNode> remainder = byConversation.entrySet().stream()
                        .filter(e -> !directMessages.contains(e.getKey()))
                        .flatMap(e -> e.getValue().stream())
                        .toList();
                if (!remainder.isEmpty()) {
                    refined.put(signature, remainder);
                }
            } else {
                refined.put(signature, group);
            }
        }
        return refined;
    }

    private Map<Signature, List<PipelineNode>> splitByConcept(
            Map<Signature, List<PipelineNode>> buckets, ConceptReader concepts) {
        Map<Signature, List<PipelineNode>> refined = new LinkedHashMap<>();

        for (Map.Entry<Signature, List<PipelineNode>> bucket : buckets.entrySet()) {
            Signature signature = bucket.getKey();
            List<PipelineNode> group = bucket.getValue();

            if (signature.activity() != null
                    || group.size() < weights.minimumNodesPerKind() * 2
                    || ConceptSplit.conceptsFor(signature.workType()).isEmpty()) {
                refined.put(signature, group);
                continue;
            }

            Map<String, PipelineNode> byId = new LinkedHashMap<>();
            Map<String, String> conceptOfNode = new LinkedHashMap<>();
            for (PipelineNode node : group) {
                byId.put(node.id(), node);
                conceptOfNode.put(node.id(), concepts.conceptOf(node));
            }

            Map<String, List<String>> groups =
                    ConceptSplit.split(conceptOfNode, signature.workType(), weights.minimumNodesPerKind());

            if (groups.size() < 2) {
                refined.put(signature, group);
                continue;
            }

            for (Map.Entry<String, List<String>> split : groups.entrySet()) {
                List<PipelineNode> members =
                        split.getValue().stream().map(byId::get).toList();
                refined.put(
                        new Signature(
                                signature.workType(),
                                signature.activity(),
                                signature.outputType(),
                                signature.conversation(),
                                split.getKey()),
                        members);
            }
        }
        return refined;
    }

    private StepKind kindOf(Signature signature, List<PipelineNode> group) {
        Set<String> jobIds = new LinkedHashSet<>();
        group.forEach(node -> jobIds.add(node.jobId()));

        double cohesion = meanPairwiseAgreement(group);

        double certainty = Math.min(1.0, (jobIds.size() / 5.0) * 0.55 + cohesion * 0.45);

        String id = "K:" + signature.workType()
                + (signature.activity() == null || signature.activity().isBlank() ? "" : "@" + signature.activity())
                + (signature.outputType() == null ? "" : ":" + signature.outputType())
                + (signature.conversation() == null ? "" : "/" + signature.conversation())
                + (signature.concept() == null || signature.concept().isBlank() ? "" : "#" + signature.concept());

        return new StepKind(
                id,
                signature.workType(),
                signature.outputType(),
                signature.conversation(),
                group.stream().map(PipelineNode::id).sorted().toList(),
                jobIds,
                commonWords(group),
                round(cohesion),
                round(certainty),
                signature.activity(),
                nameOfTheActivity(group));
    }

    private static String nameOfTheActivity(List<PipelineNode> group) {
        return group.stream()
                .map(PipelineNode::activityName)
                .filter(name -> name != null && !name.isBlank())
                .findFirst()
                .orElse(null);
    }

    private static double meanPairwiseAgreement(List<PipelineNode> group) {
        double total = 0;
        int pairs = 0;
        for (int i = 0; i < group.size(); i++) {
            for (int j = i + 1; j < group.size(); j++) {
                total += Words.trigramOverlap(group.get(i).text(), group.get(j).text());
                pairs++;
            }
        }
        return pairs == 0 ? 0.0 : total / pairs;
    }

    private static List<String> commonWords(List<PipelineNode> group) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (PipelineNode node : group) {
            for (String word : Words.tokens(node.text())) {
                if (word.length() > 3 && word.chars().allMatch(Character::isLetter)) {
                    counts.merge(word, 1, Integer::sum);
                }
            }
        }
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
                .limit(8)
                .map(Map.Entry::getKey)
                .sorted(Comparator.naturalOrder())
                .toList();
    }

    private static double round(double value) {
        return new java.math.BigDecimal(value)
                .setScale(3, java.math.RoundingMode.HALF_EVEN)
                .doubleValue();
    }

    private record Signature(
            String workType, String activity, String outputType, String conversation, String concept) {}
}
