package com.flowops.tasklib.domain.shape;

import com.flowops.tasklib.domain.TemplateDetails;
import java.text.Normalizer;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public final class HandoverLanguageSignal implements ProcessShapeSignal {
    private static final List<String> HANDOVER = List.of(
            "aprob",
            "approv",
            "trimite catre",
            "trimite la",
            "send to",
            "hand over",
            "handover",
            "cere de la",
            "asteapta raspuns",
            "wait for",
            "sign off",
            "semneaz");

    @Override
    public Optional<ProcessShapeHint> inspect(TemplateDetails details) {
        for (String item : details.checklist()) {
            String flattened = withoutDiacritics(item);
            for (String phrase : HANDOVER) {
                if (flattened.contains(phrase)) {
                    return Optional.of(new ProcessShapeHint("handover-language", item));
                }
            }
        }
        return Optional.empty();
    }

    private static String withoutDiacritics(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
