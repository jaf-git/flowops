package com.flowops.nodepipeline.domain.ai;

import com.flowops.shared.text.Words;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

public final class LabelGrounding {
    private static final Set<String> PERMITTED_GENERICS = Set.of("draft", "review", "set", "run");

    private static final int MAXIMUM_WORDS = 4;

    private LabelGrounding() {}

    public static Optional<String> groundedLabel(String proposed, List<String> sourceTexts) {
        if (proposed == null || proposed.isBlank()) {
            return Optional.empty();
        }

        List<String> labelWords = Words.tokens(proposed);
        if (labelWords.isEmpty() || labelWords.size() > MAXIMUM_WORDS) {
            return Optional.empty();
        }

        Set<String> available = new LinkedHashSet<>();
        sourceTexts.forEach(text -> available.addAll(Words.tokens(text)));

        for (String word : labelWords) {
            if (PERMITTED_GENERICS.contains(word.toLowerCase(Locale.ROOT))) {
                continue;
            }

            boolean present =
                    available.stream().anyMatch(written -> written.startsWith(word) || word.startsWith(written));
            if (!present) {
                return Optional.empty();
            }
        }
        return Optional.of(proposed.trim());
    }

    public static boolean descriptionIsGenerated(String description, List<String> sourceTexts) {
        if (description == null || description.isBlank()) {
            return true;
        }
        List<String> described = Words.tokens(description);
        if (described.size() < 4) {
            return true;
        }

        for (String source : sourceTexts) {
            List<String> written = Words.tokens(source);
            for (int i = 0; i + 4 <= described.size(); i++) {
                List<String> run = described.subList(i, i + 4);
                if (java.util.Collections.indexOfSubList(written, run) >= 0) {
                    return false;
                }
            }
        }
        return true;
    }
}
