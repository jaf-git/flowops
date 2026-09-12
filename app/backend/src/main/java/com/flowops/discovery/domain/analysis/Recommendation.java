package com.flowops.discovery.domain.analysis;

import java.util.List;
import java.util.Objects;

public record Recommendation(
        RecommendationKind kind, String headline, String detail, Finding evidence, Confidence confidence) {
    public Recommendation {
        Objects.requireNonNull(kind, "a recommendation is a kind of action");
        Objects.requireNonNull(evidence, "a recommendation with no evidence behind it is an opinion");

        headline = requireText(headline, "a recommendation says what to do");
        confidence = confidence == null ? Confidence.WORTH_LOOKING : confidence;
    }

    public enum Confidence {
        STRONG,

        WORTH_LOOKING,

        UNDERMINED
    }

    public List<com.flowops.discovery.domain.model.BracketId> subjects() {
        return evidence.subjects();
    }

    private static String requireText(String value, String why) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(why);
        }
        return value.trim();
    }
}
