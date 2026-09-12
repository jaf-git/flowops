package com.flowops.nodepipeline;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowops.nodepipeline.domain.PipelineNode;
import com.flowops.nodepipeline.domain.job.PipelineJob;
import java.io.InputStream;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

record DiscoveryFixture(
        List<PipelineNode> nodes,
        List<PipelineJob> jobs,
        Set<String> excluded,
        List<String> expectedKindIds,
        Map<String, List<String>> expectedNodesOfKind) {
    private static final ObjectMapper JSON = new ObjectMapper();

    private static final String FIXTURE_DIRECT_MESSAGE_PREFIX = "dm-";

    Set<String> directConversations() {
        return directConversationsIn(nodes);
    }

    static Set<String> directConversationsIn(List<PipelineNode> nodes) {
        return nodes.stream()
                .map(PipelineNode::conversationId)
                .filter(id -> id != null && id.startsWith(FIXTURE_DIRECT_MESSAGE_PREFIX))
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    static DiscoveryFixture load(String corpus) throws Exception {
        GoldenCorpus nodes = GoldenCorpus.load(corpus);

        try (InputStream stream =
                DiscoveryFixture.class.getResourceAsStream("/nodepipeline/golden/" + corpus + "-discovery.json")) {
            JsonNode root = JSON.readTree(stream);

            Set<String> excluded = new HashSet<>();
            root.get("churnExcluded").forEach(id -> excluded.add(id.asText()));

            List<String> kindIds = new ArrayList<>();
            Map<String, List<String>> nodesOfKind = new LinkedHashMap<>();
            root.get("stepKinds").forEach(kind -> {
                kindIds.add(kind.get("id").asText());
                List<String> ids = new ArrayList<>();
                kind.get("nodeIds").forEach(node -> ids.add(node.asText()));
                nodesOfKind.put(kind.get("id").asText(), ids);
            });

            Set<String> jobIds = new LinkedHashSet<>();
            nodes.nodes().forEach(node -> jobIds.add(node.jobId()));

            List<PipelineJob> jobs = jobIds.stream()
                    .map(id -> new PipelineJob(
                            id, id, "CLOSED", false, true, false, null, "CLIENT", LocalDate.of(2026, 7, 1)))
                    .toList();

            return new DiscoveryFixture(nodes.nodes(), jobs, excluded, kindIds, nodesOfKind);
        }
    }
}
