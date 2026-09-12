package com.flowops.discovery.application.analysis.detectors;

import com.flowops.discovery.domain.analysis.Detector;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.analysis.Stage;
import com.flowops.discovery.domain.analysis.SubjectKind;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class RecurringShape implements Detector {
    private static final int JOBS_BEFORE_ITS_A_SHAPE = 3;

    private static final int STEPS_BEFORE_ITS_A_SEQUENCE = 2;

    @Override
    public String name() {
        return "recurring-shape";
    }

    @Override
    public Stage stage() {
        return Stage.DETECT;
    }

    @Override
    public int sampleFloor() {
        return JOBS_BEFORE_ITS_A_SHAPE;
    }

    @Override
    public List<Finding> detect(GraphWindow window) {
        Map<JobId, List<GraphWindow.Row>> byJob = new LinkedHashMap<>();
        for (GraphWindow.Row row : window.evidence()) {
            byJob.computeIfAbsent(row.jobId(), key -> new ArrayList<>()).add(row);
        }

        Map<String, List<JobId>> jobsByShape = new LinkedHashMap<>();
        Map<String, List<BracketId>> bracketsByShape = new LinkedHashMap<>();

        for (Map.Entry<JobId, List<GraphWindow.Row>> job : byJob.entrySet()) {
            List<GraphWindow.Row> ordered = job.getValue().stream()
                    .sorted(Comparator.comparing(GraphWindow.Row::openedAt))
                    .toList();

            List<String> chain = collapseRepeats(
                    ordered.stream().map(GraphWindow.Row::workType).toList());

            if (chain.size() < STEPS_BEFORE_ITS_A_SEQUENCE) {
                continue;
            }

            String shape = String.join(" → ", chain);

            jobsByShape.computeIfAbsent(shape, key -> new ArrayList<>()).add(job.getKey());
            bracketsByShape
                    .computeIfAbsent(shape, key -> new ArrayList<>())
                    .addAll(ordered.stream().map(GraphWindow.Row::id).toList());
        }

        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, List<JobId>> shape : jobsByShape.entrySet()) {
            int jobs = shape.getValue().size();

            if (jobs < JOBS_BEFORE_ITS_A_SHAPE) {
                continue;
            }

            String headline = "%s ran the same way in %d jobs".formatted(shape.getKey(), jobs);

            findings.add(Finding.counted(
                    name(),
                    SubjectKind.SHAPE,
                    shape.getKey(),
                    headline,
                    jobs,
                    bracketsByShape.getOrDefault(shape.getKey(), List.of())));
        }

        return findings;
    }

    private static List<String> collapseRepeats(List<String> chain) {
        List<String> collapsed = new ArrayList<>();

        for (String step : chain) {
            if (collapsed.isEmpty() || !collapsed.get(collapsed.size() - 1).equals(step)) {
                collapsed.add(step);
            }
        }

        return collapsed;
    }
}
