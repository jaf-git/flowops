package com.flowops.nodepipeline.domain.job;

import java.util.List;

public record ProcessShape(String id, String name, List<String> steps) {
    public ProcessShape {
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
