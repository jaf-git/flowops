package com.flowops.aiassist.domain;

import java.util.List;

public record GroundedSuggestion(List<Step> steps, int evidenceLines, boolean partial) {
    public GroundedSuggestion {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }

    public record Step(String sourceKey, String text) {}
}
