package com.flowops.analyser.domain;

import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class OutputVocabulary {
    private OutputVocabulary() {}

    private static final Map<String, String> NODE_TO_TEMPLATE = Map.of(
            "TEXT", "TEXT",
            "DESIGN", "DESIGN",
            "REPORT", "REPORT",
            "SCHEDULING", "SCHEDULE",
            "NONE", "NONE");

    public static final Set<String> UNREACHABLE_FROM_A_NODE = Set.of("DECISION", "PHYSICAL");

    public static Optional<String> templateOutputKindFor(String nodeOutputType) {
        if (nodeOutputType == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(NODE_TO_TEMPLATE.get(nodeOutputType));
    }
}
