package com.flowops.aiexport.seed;

import java.util.List;

final class SeedClients {
    private SeedClients() {}

    private static final List<String> SETTLED = List.of(
            "Aurora Coffee",
            "Northwind Outdoors",
            "Lumen Skincare",
            "Harvest Table",
            "Verde Living",
            "Bright Path Clinic",
            "Ironbark Brewing",
            "Petal & Stem",
            "Sundial Travel",
            "Nimbus Fitness");

    private static final List<String> IN_FLIGHT =
            List.of("Copperleaf Interiors", "Saltwater Swim", "Fern & Fox", "Halcyon Hotels", "Bluebird Bakery");

    static String forRun(int n) {
        return SETTLED.get(Math.floorMod(n, SETTLED.size()));
    }

    static String forLiveRun(int n) {
        return IN_FLIGHT.get(Math.floorMod(n, IN_FLIGHT.size()));
    }

    static List<String> settled() {
        return SETTLED;
    }
}
