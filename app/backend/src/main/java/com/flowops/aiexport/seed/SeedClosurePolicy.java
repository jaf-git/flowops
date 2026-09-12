package com.flowops.aiexport.seed;

final class SeedClosurePolicy {
    private SeedClosurePolicy() {}

    private static final double THINNEST_SHARE = 0.4;

    private static final int STEPS = 13;

    private static final double STEP = 0.05;

    static int occurrencesNeededFor(String workTitle, int wanted) {
        return (int) Math.ceil(wanted / closureShareFor(workTitle)) + 1;
    }

    static double closureShareFor(String workTitle) {
        return THINNEST_SHARE + Math.floorMod(workTitle.hashCode() / 3, STEPS) * STEP;
    }

    static boolean closes(String workTitle, int occurrence) {
        return Math.floorMod(occurrence * 7, 20) / 20.0 < closureShareFor(workTitle);
    }

    static int closedOutOf(String workTitle, int total) {
        int closed = 0;
        for (int occurrence = 0; occurrence < total; occurrence++) {
            if (closes(workTitle, occurrence)) {
                closed++;
            }
        }
        return closed;
    }
}
