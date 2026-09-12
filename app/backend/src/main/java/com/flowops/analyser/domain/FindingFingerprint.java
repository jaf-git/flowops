package com.flowops.analyser.domain;

import java.util.Objects;

public record FindingFingerprint(String character, int reach, Integer reachOf) {
    private static final String ABSENT = "-";
    private static final char SEPARATOR = ':';

    public FindingFingerprint {
        Objects.requireNonNull(character, "a fingerprint with no character cannot answer whether the question changed");
    }

    public static FindingFingerprint of(
            Severity severity, Confidence confidence, String action, int reach, Integer reachOf) {
        String character = Integer.toHexString(Objects.hash(
                severity == null ? "" : severity.name(),
                confidence == null ? "" : confidence.name(),
                action == null ? "" : action));
        return new FindingFingerprint(character, reach, reachOf);
    }

    public static FindingFingerprint of(Finding finding) {
        return of(finding.severity(), finding.confidence(), finding.action(), finding.reach(), finding.reachOf());
    }

    public String format() {
        return character + SEPARATOR + reach + SEPARATOR + (reachOf == null ? ABSENT : reachOf.toString());
    }

    public static FindingFingerprint parse(String stored) {
        String[] parts = Objects.requireNonNull(stored, "there is no fingerprint to read")
                .split(String.valueOf(SEPARATOR));
        if (parts.length != 3) {
            throw new IllegalArgumentException("not a finding fingerprint: " + stored);
        }
        try {
            return new FindingFingerprint(
                    parts[0], Integer.parseInt(parts[1]), ABSENT.equals(parts[2]) ? null : Integer.valueOf(parts[2]));
        } catch (NumberFormatException notANumber) {
            throw new IllegalArgumentException("not a finding fingerprint: " + stored, notANumber);
        }
    }

    public boolean sameCharacterAs(FindingFingerprint other) {
        return character.equals(other.character);
    }

    public LifecycleRule.Sighting sighting() {
        return new LifecycleRule.Sighting(reach, reachOf);
    }
}
