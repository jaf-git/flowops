package com.flowops.aiassist.domain;

import java.util.List;

public record ShapeOpinion(boolean looksLikeAProcess, List<String> stepKeys, String reasoning) {
    public ShapeOpinion {
        stepKeys = stepKeys == null ? List.of() : List.copyOf(stepKeys);
    }
}
