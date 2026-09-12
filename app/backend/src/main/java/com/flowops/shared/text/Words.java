package com.flowops.shared.text;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class Words {
    private static final Set<String> GENERIC = Set.of(
            "stuff", "work", "task", "misc", "todo", "new", "update", "asap", "tbd", "urgent", "final", "v2", "ok",
            "thanks");

    private static final Set<Character> VOWELS = Set.of('a', 'e', 'i', 'o', 'u', 'y');

    private Words() {}

    public static String normalise(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        String composed = Normalizer.normalize(value, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .trim();
        String stripped =
                Normalizer.normalize(composed, Normalizer.Form.NFD).replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped.replaceAll("\\s+", " ");
    }

    public static String withoutLinksOrMarkup(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("https?://\\S+", " ")
                .replaceAll("<[^>]+>", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    public static double trigramOverlap(String one, String other) {
        Set<String> a = trigrams(normalise(one));
        Set<String> b = trigrams(normalise(other));
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> union = new HashSet<>(a);
        union.addAll(b);
        Set<String> shared = new HashSet<>(a);
        shared.retainAll(b);
        return (double) shared.size() / union.size();
    }

    public static boolean plausible(String token) {
        if (token == null || token.length() < 2) {
            return false;
        }
        for (int i = 0; i < token.length(); i++) {
            if (!Character.isLetter(token.charAt(i))) {
                return false;
            }
        }
        boolean hasVowel = token.chars().anyMatch(c -> VOWELS.contains((char) c));
        if (token.length() > 3 && !hasVowel) {
            return false;
        }
        int run = 0;
        for (int i = 0; i < token.length(); i++) {
            run = VOWELS.contains(token.charAt(i)) ? 0 : run + 1;
            if (run >= 4) {
                return false;
            }
        }
        return true;
    }

    public static double quality(String text, double plausibleFloor, double uniqueFloor) {
        String normalised = normalise(withoutLinksOrMarkup(text));
        if (normalised.isEmpty()) {
            return 0.0;
        }
        List<String> tokens = Arrays.asList(normalised.split(" "));
        List<String> meaningful = tokens.stream()
                .filter(t -> !t.isEmpty() && !GENERIC.contains(t))
                .toList();
        if (meaningful.isEmpty()) {
            return 0.0;
        }

        List<String> good = meaningful.stream().filter(Words::plausible).toList();
        if ((double) good.size() / meaningful.size() < plausibleFloor) {
            return 0.0;
        }

        double unique = (double) new HashSet<>(meaningful).size() / meaningful.size();
        if (meaningful.size() >= 4 && unique < uniqueFloor) {
            return 0.0;
        }

        return Math.min(1.0, (normalised.length() / 22.0) * 0.6 + Math.min(good.size(), 4) / 4.0 * 0.4);
    }

    public static List<String> tokens(String text) {
        String normalised = normalise(text);
        if (normalised.isEmpty()) {
            return List.of();
        }
        return Arrays.stream(normalised.split(" ")).filter(t -> !t.isEmpty()).toList();
    }

    private static Set<String> trigrams(String value) {
        String padded = "  " + value + " ";
        if (padded.length() < 3) {
            return Set.of();
        }
        Set<String> grams = new HashSet<>();
        for (int i = 0; i <= padded.length() - 3; i++) {
            grams.add(padded.substring(i, i + 3));
        }
        return grams;
    }
}
