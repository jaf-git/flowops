package com.flowops.analyser.domain;

import java.text.Normalizer;
import java.util.Locale;

public final class TitleKey {
    private TitleKey() {}

    public static String of(String title) {
        if (title == null) {
            return null;
        }
        String collapsed = Normalizer.normalize(title, Normalizer.Form.NFC)
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
        return collapsed.isEmpty() ? null : collapsed;
    }

    public static boolean sameTitle(String one, String other) {
        String left = of(one);
        String right = of(other);
        return left != null && left.equals(right);
    }
}
