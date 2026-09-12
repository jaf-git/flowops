package com.flowops.analyser.domain;

import java.util.List;
import java.util.Objects;

public record Report(
        String analyser,
        int read,
        List<Finding> findings,
        List<Absence> absences,
        List<Clean> clean,
        List<Precondition> preconditions) {
    public Report {
        Objects.requireNonNull(analyser, "a report nobody produced cannot be attributed");
        findings = findings == null ? List.of() : List.copyOf(findings);
        absences = absences == null ? List.of() : List.copyOf(absences);
        clean = clean == null ? List.of() : List.copyOf(clean);
        preconditions = preconditions == null ? List.of() : List.copyOf(preconditions);
        if (read < 0) {
            throw new IllegalArgumentException("an analyser cannot have read fewer than no things");
        }
    }

    public static Report blocked(String analyser, int read, Precondition unmet) {
        return new Report(analyser, read, List.of(), List.of(), List.of(), List.of(unmet));
    }

    public static Report clean(String analyser, int read, Clean result) {
        return new Report(analyser, read, List.of(), List.of(), List.of(result), List.of());
    }

    public boolean saysSomething() {
        return !findings.isEmpty()
                || !absences.isEmpty()
                || !clean.isEmpty()
                || preconditions.stream().anyMatch(precondition -> !precondition.met());
    }

    public boolean wasBlocked() {
        return preconditions.stream().anyMatch(precondition -> !precondition.met())
                || absences.stream().anyMatch(Absence::blocking);
    }
}
