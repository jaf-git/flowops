package com.flowops.discovery.application.shared.port;

import java.math.BigDecimal;

public interface DiscoveryThresholdPort {
    DiscoveryThresholds thresholds();

    record DiscoveryThresholds(
            int tracksToProposeType,
            int tracksToFormCandidate,
            int cyclesToConfirmLoop,
            BigDecimal orderingConsistencyRatio,
            int orderingMinTracks,
            int continuationWindowDays,
            int roleCoverageFloorPercent,
            int roleDegeneracyPercent,
            int driftWindowCompletions,
            int variantBandLowPercent,
            int variantBandHighPercent,
            BigDecimal weightDecayFactor,
            int pairingSignalsToAuto,
            int pairingSignalsToAsk) {}
}
