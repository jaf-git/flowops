package com.flowops.chatassist.domain;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

public record ConversationExtract(List<Line> lines) {
    public record Line(String speaker, String said) {}

    public ConversationExtract {
        lines = List.copyOf(lines);
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public String asText() {
        return lines.stream().map(Line::said).collect(Collectors.joining("\n"));
    }

    public Optional<String> spanFor(String phrase) {
        if (phrase == null || phrase.isBlank() || phrase.trim().length() < 2) {
            return Optional.empty();
        }

        String needle = collapsed(phrase);

        for (Line line : lines) {
            String haystack = collapsed(line.said());
            int at = haystack.toLowerCase(Locale.ROOT).indexOf(needle.toLowerCase(Locale.ROOT));
            if (at >= 0) {
                return Optional.of(originalSlice(line.said(), haystack, at, needle.length()));
            }
        }
        return Optional.empty();
    }

    private static String originalSlice(String text, String haystack, int at, int length) {
        int collapsedIndex = 0;
        int begin = -1;
        boolean lastWasSpace = false;

        for (int i = 0; i <= text.length(); i++) {
            if (collapsedIndex == at && begin < 0) {
                begin = i;
            }
            if (collapsedIndex == at + length) {
                return text.substring(begin, i).trim();
            }
            if (i == text.length()) {
                break;
            }
            char c = text.charAt(i);
            boolean space = Character.isWhitespace(c);
            if (space && lastWasSpace) {
                continue;
            }
            lastWasSpace = space;
            collapsedIndex++;
        }
        return begin < 0
                ? haystack.substring(at, at + length)
                : text.substring(begin).trim();
    }

    private static String collapsed(String text) {
        return text.replaceAll("\\s+", " ").trim();
    }
}
