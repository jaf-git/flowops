package com.flowops.chatassist.domain;

import java.util.List;

public record WorkOpinion(WorkShape shape, List<Part> parts, String reasoning) {
    public record Part(String phrase, String ownerLabel) {}

    public WorkOpinion {
        shape = shape == null ? WorkShape.NOTHING : shape;
        parts = parts == null ? List.of() : List.copyOf(parts);
    }

    public static WorkOpinion silent() {
        return new WorkOpinion(WorkShape.NOTHING, List.of(), null);
    }
}
