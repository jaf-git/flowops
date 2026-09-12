package com.flowops.workspace.domain.model;

import com.flowops.workspace.domain.exception.UnknownTimezoneException;
import java.time.ZoneId;
import java.util.List;
import java.util.Set;

public record Timezone(String value) {
    private static final List<String> KNOWN =
            ZoneId.getAvailableZoneIds().stream().sorted().toList();

    private static final Set<String> KNOWN_LOOKUP = Set.copyOf(KNOWN);

    public Timezone {
        String offered = value == null ? "" : value.trim();
        if (!KNOWN_LOOKUP.contains(offered)) {
            throw new UnknownTimezoneException(offered);
        }
        value = offered;
    }

    public static List<String> known() {
        return KNOWN;
    }
}
