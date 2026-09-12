package com.flowops.analyser.domain;

public final class LifecycleRule {
    public static final double DEFAULT_WORSENING_FACTOR = 1.5;

    public static final double DEFAULT_SHARE_DRIFT = 0.05;

    private final double worseningFactor;
    private final double shareDrift;

    public LifecycleRule(double worseningFactor, double shareDrift) {
        if (worseningFactor <= 1.0) {
            throw new IllegalArgumentException(
                    "a worsening factor at or below 1 makes every unchanged finding worsening, so nothing is: "
                            + worseningFactor);
        }
        if (shareDrift <= 0.0 || shareDrift >= 1.0) {
            throw new IllegalArgumentException(
                    "a share drift outside (0, 1) either fires on every run or on none: " + shareDrift);
        }
        this.worseningFactor = worseningFactor;
        this.shareDrift = shareDrift;
    }

    public static LifecycleRule reference() {
        return new LifecycleRule(DEFAULT_WORSENING_FACTOR, DEFAULT_SHARE_DRIFT);
    }

    public Lifecycle decide(Sighting previous, Sighting current, boolean wasDismissed) {
        if (previous == null) {
            return Lifecycle.NEW;
        }

        Direction moved = compare(previous, current);

        if (wasDismissed) {
            return moved == Direction.WORSE ? Lifecycle.WORSENING : Lifecycle.DISMISSED;
        }
        return switch (moved) {
            case WORSE -> Lifecycle.WORSENING;
            case BETTER -> Lifecycle.IMPROVING;
            case SAME -> Lifecycle.STILL_TRUE;
        };
    }

    private Direction compare(Sighting previous, Sighting current) {
        if (previous.hasPopulation() && current.hasPopulation()) {
            double before = previous.share();
            double now = current.share();
            if (now - before > shareDrift) {
                return Direction.WORSE;
            }
            if (before - now > shareDrift) {
                return Direction.BETTER;
            }
            return Direction.SAME;
        }

        if (hasWorsened(previous.reach(), current.reach())) {
            return Direction.WORSE;
        }
        if (current.reach() < previous.reach()) {
            return Direction.BETTER;
        }
        return Direction.SAME;
    }

    private boolean hasWorsened(int previousReach, int currentReach) {
        if (previousReach <= 0) {
            return currentReach > 0;
        }
        return currentReach >= previousReach * worseningFactor;
    }

    private enum Direction {
        WORSE,
        BETTER,
        SAME
    }

    public record Sighting(int reach, Integer reachOf) {
        public static Sighting of(int reach) {
            return new Sighting(reach, null);
        }

        public boolean hasPopulation() {
            return reachOf != null && reachOf > 0;
        }

        public double share() {
            return (double) reach / reachOf;
        }
    }
}
