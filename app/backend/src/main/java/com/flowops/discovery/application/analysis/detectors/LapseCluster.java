package com.flowops.discovery.application.analysis.detectors;

import com.flowops.discovery.domain.analysis.Detector;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.analysis.Stage;
import com.flowops.discovery.domain.analysis.SubjectKind;
import com.flowops.discovery.domain.enums.CloseKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class LapseCluster implements Detector {
    private static final double TOO_OFTEN = 0.2;

    @Override
    public String name() {
        return "lapse-cluster";
    }

    @Override
    public Stage stage() {
        return Stage.DETECT;
    }

    @Override
    public int sampleFloor() {
        return 5;
    }

    @Override
    public List<Finding> detect(GraphWindow window) {
        Map<String, List<GraphWindow.Row>> byWorkType = new java.util.LinkedHashMap<>();

        for (GraphWindow.Row row : window.all()) {
            if (row.boundary() || row.disrupted() || row.closeKind() == null) {
                continue;
            }
            byWorkType.computeIfAbsent(row.workType(), key -> new ArrayList<>()).add(row);
        }

        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, List<GraphWindow.Row>> group : byWorkType.entrySet()) {
            List<GraphWindow.Row> closed = group.getValue();

            if (closed.size() < sampleFloor()) {
                continue;
            }

            List<GraphWindow.Row> lapsed = closed.stream()
                    .filter(row -> row.closeKind() == CloseKind.LAPSED)
                    .toList();

            double rate = (double) lapsed.size() / closed.size();

            if (rate < TOO_OFTEN) {
                continue;
            }

            String headline = "%s work was abandoned %d times in %d — nudged once, then nothing"
                    .formatted(group.getKey(), lapsed.size(), closed.size());

            findings.add(Finding.measured(
                    name(),
                    SubjectKind.WORK_TYPE,
                    group.getKey(),
                    headline,
                    closed.size(),
                    BigDecimal.valueOf(rate * 100).setScale(1, RoundingMode.HALF_UP),
                    "% lapsed",
                    lapsed.stream().map(GraphWindow.Row::id).toList()));
        }

        return findings;
    }
}
