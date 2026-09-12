package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.EscalationIntervalsUnorderedException;
import java.util.Arrays;
import java.util.List;

public record EscalationLadder(List<Integer> hours) {
    public EscalationLadder {
        hours = hours == null ? List.of() : List.copyOf(hours);
        for (int step = 1; step < hours.size(); step++) {
            if (hours.get(step) <= hours.get(step - 1)) {
                throw new EscalationIntervalsUnorderedException(step - 1, step);
            }
        }
        if (hours.stream().anyMatch(interval -> interval <= 0)) {
            throw new EscalationIntervalsUnorderedException(0, 0);
        }
    }

    public static EscalationLadder parse(String stored) {
        if (stored == null || stored.isBlank()) {
            return new EscalationLadder(List.of());
        }
        return new EscalationLadder(Arrays.stream(stored.split(","))
                .map(String::trim)
                .map(Integer::valueOf)
                .toList());
    }

    public String stored() {
        return hours.stream()
                .map(String::valueOf)
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }
}
