package com.flowops.nodepipeline.domain.wait;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record JobElapsed(
        UUID jobId,
        Instant from,
        Instant to,
        long totalDays,
        long externalWaitDays,
        long workingDays,
        Map<WaitKind, Long> daysWaitingByKind) {
    private static final double SECONDS_PER_DAY = 86_400d;

    public static JobElapsed over(UUID jobId, Instant from, Instant to, List<WaitSpan> waits) {
        Duration total = from.isBefore(to) ? Duration.between(from, to) : Duration.ZERO;

        Map<WaitKind, List<Span>> byKind = new EnumMap<>(WaitKind.class);
        for (WaitKind kind : WaitKind.values()) {
            byKind.put(kind, new ArrayList<>());
        }
        List<Span> external = new ArrayList<>();

        for (WaitSpan wait : waits) {
            Span clamped = clamp(wait.openedAt(), wait.endedBy(to), from, to);
            if (clamped == null) {
                continue;
            }
            byKind.get(wait.kind()).add(clamped);
            if (wait.kind().external()) {
                external.add(clamped);
            }
        }

        Map<WaitKind, Long> daysByKind = new EnumMap<>(WaitKind.class);
        for (Map.Entry<WaitKind, List<Span>> entry : byKind.entrySet()) {
            daysByKind.put(entry.getKey(), days(union(entry.getValue())));
        }

        long totalDays = days(total);
        long externalDays = days(union(external));
        return new JobElapsed(jobId, from, to, totalDays, externalDays, totalDays - externalDays, daysByKind);
    }

    private static Span clamp(Instant openedAt, Instant endedAt, Instant from, Instant to) {
        Instant start = openedAt.isBefore(from) ? from : openedAt;
        Instant end = endedAt.isAfter(to) ? to : endedAt;
        return start.isBefore(end) ? new Span(start, end) : null;
    }

    private static Duration union(List<Span> spans) {
        if (spans.isEmpty()) {
            return Duration.ZERO;
        }
        List<Span> sorted = new ArrayList<>(spans);
        sorted.sort(Comparator.comparing(Span::start));

        Duration covered = Duration.ZERO;
        Instant openFrom = sorted.get(0).start();
        Instant openTo = sorted.get(0).end();
        for (Span span : sorted.subList(1, sorted.size())) {
            if (span.start().isAfter(openTo)) {
                covered = covered.plus(Duration.between(openFrom, openTo));
                openFrom = span.start();
                openTo = span.end();
            } else if (span.end().isAfter(openTo)) {
                openTo = span.end();
            }
        }
        return covered.plus(Duration.between(openFrom, openTo));
    }

    private static long days(Duration duration) {
        return Math.round(duration.toSeconds() / SECONDS_PER_DAY);
    }

    private record Span(Instant start, Instant end) {}
}
