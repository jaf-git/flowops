package com.flowops.tasklib.domain;

import java.math.BigDecimal;
import java.util.List;

public record TemplateDetails(
        String title,
        String description,
        String type,
        String priority,
        BigDecimal estimatedHours,
        List<String> checklist) {
    public TemplateDetails {
        title = title == null ? null : title.trim();
        checklist = checklist == null ? List.of() : List.copyOf(checklist);
        priority = priority == null ? "NORMAL" : priority;
    }

    public boolean hasTitle() {
        return title != null && !title.isBlank();
    }
}
