package com.flowops.task.domain.model;

import com.flowops.task.domain.enums.TaskKind;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public record TaskProvenance(TaskKind kind, UUID templateId, BigDecimal stampedEstimatedHours) {
    public TaskProvenance {
        Objects.requireNonNull(kind, "a task is of some kind");
        if (kind == TaskKind.TICKET && templateId != null) {
            throw new IllegalArgumentException("a ticket is unfiled work and cannot come from a template");
        }

        if (templateId == null && stampedEstimatedHours != null) {
            throw new IllegalArgumentException("only work stamped from a template can remember what it was told");
        }
    }

    public static TaskProvenance freeform() {
        return new TaskProvenance(TaskKind.TASK, null, null);
    }

    public static TaskProvenance fromTemplate(UUID templateId, BigDecimal stampedEstimatedHours) {
        return new TaskProvenance(
                TaskKind.TASK,
                Objects.requireNonNull(templateId, "a stamped task names its template"),
                stampedEstimatedHours);
    }

    public static TaskProvenance ticket() {
        return new TaskProvenance(TaskKind.TICKET, null, null);
    }

    public boolean isTicket() {
        return kind == TaskKind.TICKET;
    }

    public Optional<UUID> template() {
        return Optional.ofNullable(templateId);
    }

    public Optional<BigDecimal> wasToldItWouldTake() {
        return Optional.ofNullable(stampedEstimatedHours);
    }
}
