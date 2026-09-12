package com.flowops.discovery.domain.analysis;

import com.flowops.discovery.domain.model.WorkTypeCatalogue;
import java.util.Arrays;
import java.util.List;

public final class ShapeSteps {
    public static final String BETWEEN = " → ";

    private static final int FEWEST_STEPS_WORTH_WRITING = 2;

    private ShapeSteps() {}

    public static List<String> of(String shapeKey) {
        if (shapeKey == null || shapeKey.isBlank()) {
            return List.of();
        }

        List<String> steps = Arrays.stream(shapeKey.split(BETWEEN.trim()))
                .map(String::trim)
                .filter(step -> !step.isEmpty())
                .toList();

        return steps.size() < FEWEST_STEPS_WORTH_WRITING ? List.of() : steps;
    }

    public static List<String> titled(List<String> steps) {
        return steps.stream().map(WorkTypeCatalogue::titleOf).toList();
    }
}
