package com.flowops.aiassist.domain;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class EvidencePacket {
    public static final int MOST_LINES = 40;

    private final String subject;
    private final String context;
    private final List<Line> lines;
    private final boolean truncated;

    private EvidencePacket(String subject, String context, List<Line> lines, boolean truncated) {
        this.subject = subject;
        this.context = context;
        this.lines = List.copyOf(lines);
        this.truncated = truncated;
    }

    public static EvidencePacket about(String subject, String context, List<String> items) {
        List<Line> kept = new ArrayList<>();
        int index = 0;
        for (String item : items) {
            if (item == null || item.isBlank()) {
                continue;
            }
            if (kept.size() == MOST_LINES) {
                return new EvidencePacket(subject, context, kept, true);
            }
            index++;
            kept.add(new Line("c" + index, item.trim()));
        }
        return new EvidencePacket(subject, context, kept, false);
    }

    public String subject() {
        return subject;
    }

    public Optional<String> context() {
        return context == null || context.isBlank() ? Optional.empty() : Optional.of(context.trim());
    }

    public List<Line> lines() {
        return lines;
    }

    public boolean isEmpty() {
        return lines.isEmpty();
    }

    public boolean wasTruncated() {
        return truncated;
    }

    public Optional<String> textOf(String key) {
        return Optional.ofNullable(byKey().get(key));
    }

    private Map<String, String> byKey() {
        Map<String, String> index = new LinkedHashMap<>();
        for (Line line : lines) {
            index.put(line.key(), line.text());
        }
        return index;
    }

    public record Line(String key, String text) {}
}
