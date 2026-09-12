package com.flowops.analyser.domain;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Finding(
        String analyser,
        String kind,
        SubjectKind subjectKind,
        String subject,
        Category category,
        String headline,
        List<String> because,
        Map<EvidenceKind, List<String>> evidence,
        Severity severity,
        Confidence confidence,
        int reach,
        Integer reachOf,
        String action) {
    public enum EvidenceKind {
        NODE,
        BRACKET,
        JOB,
        WAIT,

        TEMPLATE
    }

    public Finding {
        Objects.requireNonNull(analyser, "a finding nobody produced cannot be attributed");
        Objects.requireNonNull(kind, "kind is half the stable key");
        Objects.requireNonNull(
                subjectKind,
                "the schema refuses a finding that will not say what sort of thing "
                        + "it is about, because that is what keeps findings off people");
        Objects.requireNonNull(subject, "subject is the other half of the key");
        Objects.requireNonNull(headline, "a finding with no headline is a row nobody reads");
        because = because == null ? List.of() : List.copyOf(because);
        evidence = evidence == null ? Map.of() : Map.copyOf(evidence);
        if (reach < 0) {
            throw new IllegalArgumentException("reach counts things, and there cannot be fewer than none of them");
        }
        if (reachOf != null && reachOf < reach) {
            throw new IllegalArgumentException(
                    "a finding cannot reach " + reach + " of " + reachOf + "; the denominator is the population");
        }
    }

    public String key() {
        return analyser + ':' + kind + ':' + subject;
    }

    public java.util.OptionalDouble share() {
        if (reachOf == null || reachOf == 0) {
            return java.util.OptionalDouble.empty();
        }
        return java.util.OptionalDouble.of((double) reach / reachOf);
    }

    public int evidenceCount() {
        return evidence.values().stream().mapToInt(List::size).sum();
    }
}
