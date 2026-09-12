package com.flowops.discovery.application.analysis.detectors;

import com.flowops.discovery.domain.analysis.Detector;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.analysis.Stage;
import com.flowops.discovery.domain.analysis.SubjectKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class Fragmentation implements Detector {
    private static final double MOSTLY_SINGLETONS = 0.8;

    @Override
    public String name() {
        return "fragmentation";
    }

    @Override
    public Stage stage() {
        return Stage.OBSERVE;
    }

    @Override
    public int sampleFloor() {
        return 5;
    }

    @Override
    public List<Finding> detect(GraphWindow window) {
        Map<String, List<GraphWindow.Row>> byWorkType = new java.util.LinkedHashMap<>();

        for (GraphWindow.Row row : window.all()) {
            if (row.boundary()) {
                continue;
            }
            byWorkType.computeIfAbsent(row.workType(), key -> new ArrayList<>()).add(row);
        }

        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, List<GraphWindow.Row>> group : byWorkType.entrySet()) {
            List<GraphWindow.Row> rows = group.getValue();

            if (rows.size() < sampleFloor()) {
                continue;
            }

            long singletons =
                    rows.stream().filter(row -> row.elapsed().toMinutes() < 1).count();

            double share = (double) singletons / rows.size();

            if (share < MOSTLY_SINGLETONS) {
                continue;
            }

            String headline = "%s opened %d brackets that never joined anything — the address may be too coarse"
                    .formatted(group.getKey(), singletons);

            findings.add(Finding.measured(
                    name(),
                    SubjectKind.WORK_TYPE,
                    group.getKey(),
                    headline,
                    rows.size(),
                    BigDecimal.valueOf(share * 100).setScale(1, RoundingMode.HALF_UP),
                    "% singletons",
                    rows.stream().map(GraphWindow.Row::id).toList()));
        }

        return findings;
    }
}
