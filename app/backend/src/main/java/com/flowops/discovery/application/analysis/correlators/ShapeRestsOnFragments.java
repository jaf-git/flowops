package com.flowops.discovery.application.analysis.correlators;

import com.flowops.discovery.domain.analysis.Correlator;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.analysis.SubjectKind;
import com.flowops.discovery.domain.model.BracketId;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class ShapeRestsOnFragments implements Correlator {
    @Override
    public String name() {
        return "shape-rests-on-fragments";
    }

    @Override
    public int sampleFloor() {
        return 2;
    }

    @Override
    public List<Finding> correlate(GraphWindow window, List<Finding> found) {
        List<Finding> shapes =
                found.stream().filter(f -> f.subjectKind() == SubjectKind.SHAPE).toList();

        if (shapes.isEmpty()) {
            return List.of();
        }

        Set<String> fragmented = new LinkedHashSet<>();
        for (Finding finding : found) {
            if ("fragmentation".equals(finding.detector())) {
                fragmented.add(finding.subjectKey());
            }
        }

        if (fragmented.isEmpty()) {
            return List.of();
        }

        List<Finding> correlations = new ArrayList<>();

        for (Finding shape : shapes) {
            List<String> steps = List.of(shape.subjectKey().split("\\s*→\\s*"));

            List<String> affected = steps.stream().filter(fragmented::contains).toList();

            if (affected.isEmpty()) {
                continue;
            }

            String headline = ("%s recurred in %d jobs, but %d of its %d steps are work types that never joined "
                            + "anything — fix the addresses before writing this down")
                    .formatted(shape.subjectKey(), shape.sampleSize(), affected.size(), steps.size());

            List<BracketId> subjects = shape.subjects();

            correlations.add(Finding.counted(
                    name(), SubjectKind.SHAPE, shape.subjectKey(), headline, affected.size(), subjects));
        }

        return correlations;
    }
}
