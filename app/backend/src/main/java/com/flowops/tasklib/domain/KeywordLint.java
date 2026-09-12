package com.flowops.tasklib.domain;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class KeywordLint {
    private static final Set<String> GENERIC = Set.of(
            "stuff", "work", "task", "misc", "todo", "new", "update", "asap", "tbd", "urgent", "final", "v2", "ok",
            "thanks", "publish", "review", "send", "check", "done", "start", "finish", "post", "make", "do");

    private KeywordLint() {}

    public enum Reason {
        TOO_GENERIC,

        ALREADY_IN_THE_TITLE,

        BLANK,

        DUPLICATE
    }

    public record Fault(String keyword, Reason reason) {}

    public static List<Fault> check(String title, List<String> keywords) {
        if (keywords == null || keywords.isEmpty()) {
            return List.of();
        }

        String normalisedTitle = fold(title);
        Set<String> alreadySeen = new LinkedHashSet<>();
        List<Fault> faults = new ArrayList<>();

        for (String keyword : keywords) {
            if (keyword == null || keyword.isBlank()) {
                faults.add(new Fault(keyword, Reason.BLANK));
                continue;
            }

            String folded = fold(keyword);
            if (!alreadySeen.add(folded)) {
                faults.add(new Fault(keyword, Reason.DUPLICATE));
                continue;
            }

            if (!normalisedTitle.isEmpty() && normalisedTitle.contains(folded)) {
                faults.add(new Fault(keyword, Reason.ALREADY_IN_THE_TITLE));
                continue;
            }

            if (!folded.contains(" ") && GENERIC.contains(folded)) {
                faults.add(new Fault(keyword, Reason.TOO_GENERIC));
            }
        }

        return List.copyOf(faults);
    }

    public static boolean isClean(String title, List<String> keywords) {
        return check(title, keywords).isEmpty();
    }

    private static String fold(String value) {
        if (value == null) {
            return "";
        }
        String stripped = Normalizer.normalize(value.trim(), Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");
    }
}
