package com.flowops.aiassist.api.dto;

import com.flowops.aiassist.domain.GroundedSuggestion;
import java.util.List;

public record ShapeSuggestionResponse(
        boolean available, boolean suggested, List<Step> steps, int evidenceLines, boolean partial) {
    public static ShapeSuggestionResponse unavailable() {
        return new ShapeSuggestionResponse(false, false, List.of(), 0, false);
    }

    public static ShapeSuggestionResponse nothing() {
        return new ShapeSuggestionResponse(true, false, List.of(), 0, false);
    }

    public static ShapeSuggestionResponse of(GroundedSuggestion suggestion) {
        return new ShapeSuggestionResponse(
                true,
                true,
                suggestion.steps().stream()
                        .map(step -> new Step(step.sourceKey(), step.text()))
                        .toList(),
                suggestion.evidenceLines(),
                suggestion.partial());
    }

    public record Step(String sourceKey, String text) {}
}
