package com.flowops.tasklib.domain;

import com.flowops.tasklib.domain.exception.IllegalTemplateTransitionException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

public record TemplateSchedule(
        UUID id,
        UUID templateId,
        UUID assigneeId,
        UUID createdBy,
        Recurrence recurrence,
        boolean active,
        Instant createdAt,
        Instant updatedAt) {
    public static TemplateSchedule set(
            UUID templateId, UUID assigneeId, UUID createdBy, Recurrence recurrence, Instant now) {
        return new TemplateSchedule(UUID.randomUUID(), templateId, assigneeId, createdBy, recurrence, true, now, now);
    }

    public boolean fallsDueOn(LocalDate date, ZoneId zone) {
        if (!active || date.isBefore(LocalDate.ofInstant(createdAt, zone))) {
            return false;
        }
        return recurrence.fallsOn(date);
    }

    public LocalDate nextOccurrenceOnOrAfter(LocalDate from) {
        return active ? recurrence.nextOnOrAfter(from) : null;
    }

    public TemplateSchedule paused(Instant now) {
        if (!active) {
            throw new IllegalTemplateTransitionException("this schedule is already paused");
        }
        return new TemplateSchedule(id, templateId, assigneeId, createdBy, recurrence, false, createdAt, now);
    }

    public TemplateSchedule resumed(Instant now) {
        if (active) {
            throw new IllegalTemplateTransitionException("this schedule is already running");
        }
        return new TemplateSchedule(id, templateId, assigneeId, createdBy, recurrence, true, createdAt, now);
    }
}
