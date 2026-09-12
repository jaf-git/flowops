package com.flowops.shared.text;

import java.util.List;

public enum Intent {
    WORK,

    NEGATED,

    META,

    QUESTION;

    private static final List<String> NEGATION =
            List.of("do not ", "don't ", "dont ", "skip ", "cancel ", "postpone ", "no need", "hold off", "paused");

    private static final List<String> META_MARKERS = List.of(
            "advice on",
            "training:",
            "how to ",
            "who should",
            "who owns",
            "can we reuse",
            "which template",
            "should we ",
            "question about",
            "reminder to",
            "fyi",
            "discuss ");

    private static final List<String> ASKS = List.of(
            "where ",
            "when ",
            "who ",
            "why ",
            "how ",
            "what ",
            "which ",
            "can we ",
            "should we ",
            "is there ",
            "are the ",
            "do we ",
            "does anyone ",
            "anyone got ");

    public static Intent of(String text, String detail) {
        String said = Words.normalise(Words.withoutLinksOrMarkup(text)).trim();
        String subject = said + " " + Words.normalise(Words.withoutLinksOrMarkup(detail));

        if (NEGATION.stream().anyMatch(subject::contains)) {
            return NEGATED;
        }
        if (META_MARKERS.stream().anyMatch(subject::contains)) {
            return META;
        }
        String trimmed = subject.trim();
        if (trimmed.endsWith("?")) {
            return QUESTION;
        }
        if (opensLikeAQuestion(said.isEmpty() ? trimmed : said)) {
            return QUESTION;
        }
        return WORK;
    }

    private static boolean opensLikeAQuestion(String said) {
        if (said.endsWith(".")) {
            return false;
        }
        return ASKS.stream().anyMatch(said::startsWith);
    }
}
