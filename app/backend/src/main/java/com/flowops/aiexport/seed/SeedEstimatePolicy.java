package com.flowops.aiexport.seed;

final class SeedEstimatePolicy {
    private SeedEstimatePolicy() {}

    private static final int SHORTEST_HOURS = 1;

    private static final int LONGEST_HOURS = 4;

    record Guess(int actualWorkHours, Double estimateHours) {
        String estimateJson() {
            return estimateHours == null ? "null" : String.valueOf(estimateHours);
        }

        boolean wasEstimated() {
            return estimateHours != null;
        }
    }

    static Guess guessFor(String title) {
        int actual = SHORTEST_HOURS + Math.floorMod(title.hashCode() / 7, LONGEST_HOURS - SHORTEST_HOURS + 1);
        return switch (Math.floorMod(title.hashCode(), 6)) {
            case 0 -> new Guess(actual, null);
            case 1, 2 -> new Guess(actual, (double) actual);
            case 3 -> new Guess(actual, actual / 2.0);
            case 4 -> new Guess(actual, actual * 2.0);
            default -> new Guess(actual, actual * 3.0);
        };
    }

    static Double estimateTypedOnTheLibraryScreen(String title) {
        return guessFor(title).estimateHours();
    }
}
