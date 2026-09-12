package com.flowops.analyser.domain;

public final class PriorityRule {
    public static final int DEFAULT_GRACE = 2;

    public static final double DEFAULT_DECAY = 0.8;

    public static final double DEFAULT_FLOOR = 0.25;

    private static final double SHARE_WHEN_THERE_IS_NO_POPULATION = 0.5;

    private final int grace;
    private final double decayFactor;
    private final double floor;

    public PriorityRule(int grace, double decayFactor, double floor) {
        if (grace < 1) {
            throw new IllegalArgumentException(
                    "a grace below one appearance fades a finding the first time it is shown: " + grace);
        }
        if (decayFactor <= 0.0 || decayFactor >= 1.0) {
            throw new IllegalArgumentException(
                    "a decay factor outside (0, 1) either never fades or erases in one run: " + decayFactor);
        }
        if (floor <= 0.0 || floor > 1.0) {
            throw new IllegalArgumentException(
                    "a floor at or below zero lets a true finding disappear entirely: " + floor);
        }
        this.grace = grace;
        this.decayFactor = decayFactor;
        this.floor = floor;
    }

    public static PriorityRule reference() {
        return new PriorityRule(DEFAULT_GRACE, DEFAULT_DECAY, DEFAULT_FLOOR);
    }

    public Ranked rank(
            Severity severity, Confidence confidence, int reach, Integer reachOf, Lifecycle lifecycle, int timesSeen) {
        double share = reachOf == null || reachOf == 0 ? SHARE_WHEN_THERE_IS_NO_POPULATION : (double) reach / reachOf;
        double decay = decayOf(lifecycle, timesSeen);

        double score =
                weightOf(severity) * weightOf(confidence) * (0.5 + 0.3 * share + 0.2 * trendOf(lifecycle)) * decay;

        return new Ranked(score, because(severity, confidence, reach, reachOf, lifecycle, timesSeen, decay));
    }

    private double decayOf(Lifecycle lifecycle, int timesSeen) {
        boolean fades = lifecycle == Lifecycle.STILL_TRUE || lifecycle == Lifecycle.IMPROVING;
        if (!fades || timesSeen <= grace) {
            return 1.0;
        }
        return Math.max(floor, Math.pow(decayFactor, timesSeen - grace));
    }

    private static double weightOf(Severity severity) {
        if (severity == null) {
            return 0.25;
        }
        return switch (severity) {
            case CRITICAL -> 1.0;
            case HIGH -> 0.75;
            case MEDIUM -> 0.5;
            case LOW -> 0.25;
        };
    }

    private static double weightOf(Confidence confidence) {
        if (confidence == null) {
            return 0.4;
        }
        return switch (confidence) {
            case HIGH -> 1.0;
            case MEDIUM -> 0.7;
            case LOW -> 0.4;
        };
    }

    private static double trendOf(Lifecycle lifecycle) {
        return switch (lifecycle) {
            case WORSENING -> 1.0;
            case NEW -> 0.8;
            case STILL_TRUE -> 0.5;
            case IMPROVING -> 0.2;
            case RESOLVED, DISMISSED -> 0.0;
        };
    }

    private String because(
            Severity severity,
            Confidence confidence,
            int reach,
            Integer reachOf,
            Lifecycle lifecycle,
            int timesSeen,
            double decay) {
        StringBuilder line = new StringBuilder()
                .append(severity == null ? "Unranked" : sentenceCase(severity.name()))
                .append(" because it touches ")
                .append(reach);
        if (reachOf != null) {
            line.append(" of ").append(reachOf);
        }
        line.append(" and ").append(trendPhrase(lifecycle)).append('.');

        if (confidence == Confidence.LOW) {
            line.append(" The analyser is not certain of this one.");
        }

        if (decay < 1.0) {
            line.append(" Shown ")
                    .append(timesSeen)
                    .append(lifecycle == Lifecycle.IMPROVING ? " times, and still improving." : " times, unchanged.");
        }
        return line.toString();
    }

    private static String trendPhrase(Lifecycle lifecycle) {
        return switch (lifecycle) {
            case NEW -> "it is new since the last run";
            case WORSENING -> "it is worse than last run";
            case IMPROVING -> "it is better than last run";
            case STILL_TRUE -> "it is unchanged since last run";
            case RESOLVED -> "it has been resolved";
            case DISMISSED -> "you have said no to it";
        };
    }

    private static String sentenceCase(String shouted) {
        return shouted.charAt(0) + shouted.substring(1).toLowerCase(java.util.Locale.ROOT);
    }

    public record Ranked(double score, String because) {}
}
