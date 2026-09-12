package com.flowops.discovery.application.analysis.detectors;

import com.flowops.discovery.domain.analysis.Detector;
import com.flowops.discovery.domain.analysis.Finding;
import com.flowops.discovery.domain.analysis.GraphWindow;
import com.flowops.discovery.domain.analysis.Stage;
import com.flowops.discovery.domain.analysis.SubjectKind;
import com.flowops.discovery.domain.model.BracketId;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

@Component
public class WaitDominatedWork implements Detector {
    private static final double MOSTLY = 0.5;

    @Override
    public String name() {
        return "wait-dominated-work";
    }

    @Override
    public Stage stage() {
        return Stage.DETECT;
    }

    @Override
    public int sampleFloor() {
        return 4;
    }

    @Override
    public List<Finding> detect(GraphWindow window) {
        List<Finding> findings = new ArrayList<>();

        for (Map.Entry<String, List<GraphWindow.Row>> group :
                window.evidenceByWorkType().entrySet()) {
            List<GraphWindow.Row> rows = group.getValue();

            if (rows.size() < sampleFloor()) {
                continue;
            }

            Duration working = total(rows, GraphWindow.Row::working);
            Duration internal = total(rows, GraphWindow.Row::internalWaiting);
            Duration external = total(rows, GraphWindow.Row::externalWaiting);
            Duration elapsed = working.plus(internal).plus(external);

            if (elapsed.isZero()) {
                continue;
            }

            double waitingShare = (double) (internal.toSeconds() + external.toSeconds()) / elapsed.toSeconds();

            if (waitingShare <= MOSTLY) {
                continue;
            }

            double externalShare = (double) external.toSeconds() / elapsed.toSeconds();

            String headline = "%s work is %d%% waiting, and %d points of that is outside the workspace"
                    .formatted(group.getKey(), percent(waitingShare), percent(externalShare));

            findings.add(Finding.measured(
                    name(),
                    SubjectKind.WORK_TYPE,
                    group.getKey(),
                    headline,
                    rows.size(),
                    BigDecimal.valueOf(waitingShare * 100).setScale(1, RoundingMode.HALF_UP),
                    "% waiting",
                    rows.stream().map(GraphWindow.Row::id).toList()));
        }

        return findings;
    }

    private static Duration total(
            List<GraphWindow.Row> rows, java.util.function.Function<GraphWindow.Row, Duration> part) {
        return rows.stream().map(part).reduce(Duration.ZERO, Duration::plus);
    }

    private static int percent(double share) {
        return (int) Math.round(share * 100);
    }

    @SuppressWarnings("unused")
    private static List<BracketId> noSubjects() {
        return List.of();
    }
}
