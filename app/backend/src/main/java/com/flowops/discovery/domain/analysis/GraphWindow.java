package com.flowops.discovery.domain.analysis;

import com.flowops.discovery.domain.enums.CloseKind;
import com.flowops.discovery.domain.enums.WaitKind;
import com.flowops.discovery.domain.model.BracketId;
import com.flowops.discovery.domain.model.JobId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

public record GraphWindow(Instant from, Instant to, List<Row> rows) {
    public GraphWindow {
        Objects.requireNonNull(from, "a window starts somewhere");
        Objects.requireNonNull(to, "a window ends somewhere");

        if (!to.isAfter(from)) {
            throw new IllegalArgumentException("a window runs forwards; " + from + " to " + to + " does not");
        }

        rows = List.copyOf(rows);
    }

    public record Row(
            BracketId id,
            JobId jobId,
            String workType,
            String parentWorkType,
            CloseKind closeKind,
            boolean disrupted,
            boolean boundary,
            Instant openedAt,
            Instant closedAt,
            Duration working,
            Map<WaitKind, Duration> waitingByKind,
            int openWaits,
            boolean nudged,
            boolean answeredNudge) {
        public Row {
            waitingByKind = Map.copyOf(waitingByKind);
        }

        public boolean isEvidence() {
            return !disrupted && !boundary && closeKind != null && closeKind.countsAsPatternEvidence();
        }

        public Duration internalWaiting() {
            return waitingByKind.entrySet().stream()
                    .filter(entry -> !entry.getKey().isExternal())
                    .map(Map.Entry::getValue)
                    .reduce(Duration.ZERO, Duration::plus);
        }

        public Duration externalWaiting() {
            return waitingByKind.entrySet().stream()
                    .filter(entry -> entry.getKey().isExternal())
                    .map(Map.Entry::getValue)
                    .reduce(Duration.ZERO, Duration::plus);
        }

        public Duration elapsed() {
            return working.plus(internalWaiting()).plus(externalWaiting());
        }
    }

    public List<Row> evidence() {
        return rows.stream().filter(Row::isEvidence).toList();
    }

    public Map<String, List<Row>> evidenceByWorkType() {
        return evidence().stream().collect(Collectors.groupingBy(Row::workType));
    }

    public Map<String, List<Row>> evidenceByRolePair() {
        return evidence().stream()
                .filter(row -> row.parentWorkType() != null)
                .collect(Collectors.groupingBy(row -> row.parentWorkType() + "→" + row.workType()));
    }

    public List<Row> all() {
        return rows;
    }

    public boolean isEmpty() {
        return rows.isEmpty();
    }
}
