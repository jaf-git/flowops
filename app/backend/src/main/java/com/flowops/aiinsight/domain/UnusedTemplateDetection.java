package com.flowops.aiinsight.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class UnusedTemplateDetection {
    public static final int WINDOW_DAYS = 90;

    public static final int NEVER = -1;

    private UnusedTemplateDetection() {}

    public record TemplateUsage(
            UUID templateId, String name, Instant approvedAt, Instant lastUsedAt, int timesUsed, List<Instant> usedAt) {
        public TemplateUsage {
            usedAt = usedAt == null ? List.of() : List.copyOf(usedAt);
        }
    }

    public record UnusedTemplate(
            UUID templateId,
            String name,
            Instant lastUsedAt,
            int timesUsed,
            int daysSinceLastUse,
            int windowDays,
            Integer expectedIntervalDays) {
        public boolean everUsed() {
            return lastUsedAt != null;
        }

        public boolean judgedAgainstARhythm() {
            return expectedIntervalDays != null;
        }
    }

    public static List<UnusedTemplate> over(List<TemplateUsage> library, Instant now) {
        return over(library, now, WINDOW_DAYS);
    }

    public static List<UnusedTemplate> over(List<TemplateUsage> library, Instant now, int fallbackWindowDays) {
        List<UnusedTemplate> found = new ArrayList<>();
        for (TemplateUsage template : library) {
            UsageCadence cadence = UsageCadence.in(template.usedAt());
            int idleAfter = cadence.idleAfterDays(fallbackWindowDays);
            Instant windowOpened = now.minus(Duration.ofDays(idleAfter));

            Instant approvalCutoff = now.minus(Duration.ofDays(fallbackWindowDays));
            if (template.approvedAt() == null || !template.approvedAt().isBefore(approvalCutoff)) {
                continue;
            }

            if (template.lastUsedAt() != null && !template.lastUsedAt().isBefore(windowOpened)) {
                continue;
            }
            found.add(new UnusedTemplate(
                    template.templateId(),
                    template.name(),
                    template.lastUsedAt(),
                    template.timesUsed(),
                    daysSince(template.lastUsedAt(), now),
                    idleAfter,
                    cadence.periodDays()));
        }

        found.sort(Comparator.comparing((UnusedTemplate stale) -> stale.everUsed() ? 0 : 1)
                .reversed()
                .thenComparing(Comparator.comparingInt(UnusedTemplate::daysSinceLastUse)
                        .reversed()));
        return List.copyOf(found);
    }

    private static int daysSince(Instant lastUsed, Instant now) {
        return lastUsed == null ? NEVER : (int) Duration.between(lastUsed, now).toDays();
    }
}
