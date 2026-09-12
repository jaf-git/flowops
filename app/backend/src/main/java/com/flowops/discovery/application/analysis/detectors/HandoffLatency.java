package com.flowops.discovery.application.analysis.detectors;

import com.flowops.discovery.domain.analysis.Detector;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.analysis.Stage;
import com.flowops.discovery.domain.analysis.SubjectKind;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class HandoffLatency implements Detector {
    private static final Duration WORTH_SAYING = Duration.ofHours(4);

    @Override
    public String name() {
        return "handoff-latency";
    }

    @Override
    public Stage stage() {
        return Stage.DETECT;
    }

    @Override
    public List<Finding> detect(GraphWindow window) {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, List<GraphWindow.Row>> seam :
                window.evidenceByRolePair().entrySet()) {
            List<GraphWindow.Row> rows = seam.getValue();

            if (rows.size() < sampleFloor()) {
                continue;
            }

            Duration median = medianInternalWait(rows);

            if (median.compareTo(WORTH_SAYING) < 0) {
                continue;
            }

            double days = median.toMinutes() / (60.0 * 24.0);

            String headline = "work handed from %s waits %.1f days before it moves"
                    .formatted(seam.getKey().replace("→", " to "), days);

            findings.add(Finding.measured(
                    name(),
                    SubjectKind.ROLE_PAIR,
                    seam.getKey(),
                    headline,
                    rows.size(),
                    BigDecimal.valueOf(days).setScale(2, RoundingMode.HALF_UP),
                    "days",
                    rows.stream().map(GraphWindow.Row::id).toList()));
        }

        return findings;
    }

    private static Duration medianInternalWait(List<GraphWindow.Row> rows) {
        List<Long> seconds = rows.stream()
                .map(row -> row.internalWaiting().toSeconds())
                .sorted()
                .toList();

        int middle = seconds.size() / 2;

        long value =
                seconds.size() % 2 == 1 ? seconds.get(middle) : (seconds.get(middle - 1) + seconds.get(middle)) / 2;

        return Duration.ofSeconds(value);
    }
}
