package com.flowops.aiassist.domain;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class GroundingValidator {
    private static final double MOST_THAT_MAY_DROP = 0.5;

    private static final int FEWEST_STEPS = 2;

    private GroundingValidator() {}

    public static Optional<GroundedSuggestion> check(ShapeOpinion opinion, EvidencePacket evidence) {
        if (opinion == null || !opinion.looksLikeAProcess()) {
            return Optional.empty();
        }
        List<GroundedSuggestion.Step> grounded = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        int proposed = opinion.stepKeys().size();

        for (String key : opinion.stepKeys()) {
            if (key == null || key.isBlank() || !seen.add(key)) {
                continue;
            }
            evidence.textOf(key).ifPresent(text -> grounded.add(new GroundedSuggestion.Step(key, text)));
        }

        if (proposed == 0 || grounded.size() < FEWEST_STEPS) {
            return Optional.empty();
        }

        if ((double) (proposed - grounded.size()) / proposed > MOST_THAT_MAY_DROP) {
            return Optional.empty();
        }

        return Optional.of(new GroundedSuggestion(
                grounded, evidence.lines().size(), grounded.size() < proposed || evidence.wasTruncated()));
    }
}
