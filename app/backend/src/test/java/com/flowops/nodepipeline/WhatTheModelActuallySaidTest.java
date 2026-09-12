package com.flowops.nodepipeline;

import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.ai.ConceptSplit;
import com.flowops.nodepipeline.domain.ai.Judgement;
import com.flowops.nodepipeline.domain.discovery.DiscoveredProcess;
import com.flowops.nodepipeline.domain.discovery.DiscoveryWeights;
import com.flowops.nodepipeline.domain.discovery.ProcessDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepDiscovery;
import com.flowops.nodepipeline.domain.discovery.StepKind;
import com.flowops.nodepipeline.infrastructure.model.OllamaWorkJudgeAdapter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("NODEPIPE-AI-01")
@Tag("livemodel")
class WhatTheModelActuallySaidTest {
    @Test
    void printWhatTheModelSaidAndWhatItChanged() throws Exception {
        DiscoveryFixture corpus = DiscoveryFixture.load("multichat-extreme");
        DiscoveryWeights weights = DiscoveryWeights.reference();

        OllamaWorkJudgeAdapter model = new OllamaWorkJudgeAdapter(
                "http://localhost:11434",
                "llama3.2:3b",
                30,
                400,
                false,
                true,
                false,
                false,
                new com.flowops.nodepipeline.application.AiSwitch());

        Map<String, String> saidOf = new LinkedHashMap<>();
        StepDiscovery.ConceptReader recording = node -> {
            String concept = conceptOf(model, node);
            saidOf.put(node.id(), concept);
            return concept;
        };

        List<StepKind> without =
                new StepDiscovery(weights, corpus.directConversations()).discover(corpus.nodes(), corpus.excluded());
        List<StepKind> with = new StepDiscovery(weights, corpus.directConversations())
                .refinedForDrafting(corpus.nodes(), corpus.excluded(), recording);

        List<DiscoveredProcess> withoutProcesses =
                new ProcessDiscovery(weights).discover(corpus.jobs(), corpus.nodes(), without);
        List<DiscoveredProcess> withProcesses =
                new ProcessDiscovery(weights).discover(corpus.jobs(), corpus.nodes(), with);

        System.out.println("\n================ WHAT THE MODEL SAID ================");

        Map<String, Integer> tally = new TreeMap<>();
        saidOf.values().forEach(concept -> tally.merge(concept == null ? "(no answer)" : concept, 1, Integer::sum));
        System.out.println("\n-- concepts assigned, by frequency --");
        tally.forEach((concept, count) -> System.out.printf("  %-14s %d%n", concept, count));

        System.out.println("\n-- step kinds WITHOUT the model (" + without.size() + ") --");
        without.forEach(kind -> System.out.printf(
                "  %-34s %d nodes  cohesion %.2f%n", kind.id(), kind.nodeIds().size(), kind.cohesion()));

        System.out.println("\n-- step kinds WITH the model (" + with.size() + ") --");
        with.forEach(kind -> System.out.printf(
                "  %-34s %d nodes  cohesion %.2f%n", kind.id(), kind.nodeIds().size(), kind.cohesion()));

        System.out.println("\n-- what the split did --");
        Set<String> beforeIds = without.stream().map(StepKind::id).collect(java.util.stream.Collectors.toSet());
        Set<String> afterIds = with.stream().map(StepKind::id).collect(java.util.stream.Collectors.toSet());
        beforeIds.stream().filter(id -> !afterIds.contains(id)).forEach(id -> System.out.println("  GONE:  " + id));
        afterIds.stream().filter(id -> !beforeIds.contains(id)).forEach(id -> System.out.println("  NEW:   " + id));

        System.out.println("\n-- processes WITHOUT the model (" + withoutProcesses.size() + ") --");
        withoutProcesses.forEach(p -> System.out.printf("  runs=%d  core=%s%n", p.runs(), p.core()));

        System.out.println("\n-- processes WITH the model (" + withProcesses.size() + ") --");
        withProcesses.forEach(p -> System.out.printf("  runs=%d  core=%s%n", p.runs(), p.core()));

        System.out.println("\n-- the sample the judgement rests on --");
        int shown = 0;
        for (Map.Entry<String, String> said : saidOf.entrySet()) {
            if (said.getValue() == null || shown++ >= 25) {
                continue;
            }
            corpus.nodes().stream()
                    .filter(node -> node.id().equals(said.getKey()))
                    .findFirst()
                    .ifPresent(node -> System.out.printf(
                            "  %-10s -> %-12s  %s%n", node.workType(), said.getValue(), truncated(node.text())));
        }
        System.out.println("\n=====================================================\n");
    }

    private static String truncated(String text) {
        String flat = text == null ? "" : text.replaceAll("\\s+", " ").trim();
        return flat.length() <= 70 ? flat : flat.substring(0, 70) + "...";
    }

    private static String conceptOf(OllamaWorkJudgeAdapter model, PipelineNode node) {
        Set<String> allowed = ConceptSplit.conceptsFor(node.workType());
        if (allowed.isEmpty()) {
            return null;
        }
        return model.judge(new Judgement.Question(
                        Judgement.PlugPoint.SAME_KIND, node.text(), List.of(), String.join(", ", allowed)))
                .map(Judgement.Verdict::value)
                .filter(allowed::contains)
                .orElse(null);
    }
}
