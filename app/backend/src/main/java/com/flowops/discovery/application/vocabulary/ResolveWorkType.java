package com.flowops.discovery.application.vocabulary;

import com.flowops.discovery.application.shared.port.GovernedWorkTypePort;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class ResolveWorkType {
    private static final int CURRENT_FAMILY_VERSION = 1;

    private final GovernedWorkTypePort governed;

    public ResolveWorkType(GovernedWorkTypePort governed) {
        this.governed = governed;
    }

    public Optional<String> of(String written) {
        if (written == null || written.isBlank()) {
            return Optional.empty();
        }

        String folded = written.trim().toLowerCase(Locale.ROOT).replaceAll("\\s+", " ");

        for (GovernedWorkTypePort.WorkTypeEntry entry : governed.vocabulary()) {
            if (entry.code().toLowerCase(Locale.ROOT).equals(folded)) {
                return Optional.of(entry.code());
            }
        }

        return Optional.ofNullable(governed.aliases().get(folded));
    }

    public List<GovernedWorkTypePort.WorkTypeEntry> assignable() {
        return governed.vocabulary().stream()
                .filter(GovernedWorkTypePort.WorkTypeEntry::active)
                .sorted(java.util.Comparator.comparingInt(GovernedWorkTypePort.WorkTypeEntry::displayOrder))
                .toList();
    }

    public double nearness(String one, String other) {
        if (one == null || other == null) {
            return 0.0;
        }
        if (one.equals(other)) {
            return 1.0;
        }
        return family().getOrDefault(GovernedWorkTypePort.TypePair.of(one, other), 0.0);
    }

    public Map<String, String> labels() {
        Map<String, String> labels = new LinkedHashMap<>();
        for (GovernedWorkTypePort.WorkTypeEntry entry : governed.vocabulary()) {
            labels.put(entry.code(), entry.label());
        }
        return Map.copyOf(labels);
    }

    private Map<GovernedWorkTypePort.TypePair, Double> family() {
        return governed.family(CURRENT_FAMILY_VERSION);
    }
}
