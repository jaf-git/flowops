package com.flowops.nodepipeline.domain.match;

import com.flowops.shared.text.Words;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public record Lexicons(Map<RolePair, Double> roleKinship, Map<String, List<String>> typeWords) {
    public Lexicons {
        roleKinship = Map.copyOf(roleKinship);
        Map<String, List<String>> folded = new LinkedHashMap<>();
        typeWords.forEach((type, words) -> folded.put(
                type, words.stream().map(w -> w.toLowerCase(Locale.ROOT)).toList()));
        typeWords = Map.copyOf(folded);
    }

    public record RolePair(String a, String b) {
        public static RolePair of(String one, String other) {
            return new RolePair(one, other);
        }
    }

    public static Lexicons empty() {
        return new Lexicons(Map.of(), Map.of());
    }

    public double kinship(String one, String other) {
        if (one == null || other == null) {
            return 0.0;
        }
        if (one.equals(other)) {
            return 1.0;
        }
        Double direct = roleKinship.get(RolePair.of(one, other));
        if (direct != null) {
            return direct;
        }
        return roleKinship.getOrDefault(RolePair.of(other, one), 0.0);
    }

    public Optional<String> typeHintFrom(String text, String detail) {
        String subject = Words.normalise((text == null ? "" : text) + " " + (detail == null ? "" : detail));

        String only = null;
        for (Map.Entry<String, List<String>> entry : typeWords.entrySet()) {
            boolean hit = entry.getValue().stream().anyMatch(subject::contains);
            if (hit) {
                if (only != null) {
                    return Optional.empty();
                }
                only = entry.getKey();
            }
        }
        return Optional.ofNullable(only);
    }
}
