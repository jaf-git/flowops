package com.flowops.tasklib.api.dto;

import com.flowops.tasklib.application.TemplateScheduleUseCase;
import com.flowops.tasklib.domain.ScheduleCadence;
import com.flowops.tasklib.domain.TemplateSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

public record TemplateScheduleResponse(
        UUID id,
        UUID templateId,
        UUID assigneeId,
        ScheduleCadence cadence,
        Integer dayOfWeek,
        Integer dayOfMonth,
        boolean active,
        LocalDate nextOccurrence,
        int timesRaised,
        LocalDate lastRaisedOn,
        Instant createdAt) {
    public static TemplateScheduleResponse of(TemplateScheduleUseCase.Scheduled scheduled) {
        TemplateSchedule schedule = scheduled.schedule();
        return new TemplateScheduleResponse(
                schedule.id(),
                schedule.templateId(),
                schedule.assigneeId(),
                schedule.recurrence().cadence(),
                schedule.recurrence().dayOfWeek(),
                schedule.recurrence().dayOfMonth(),
                schedule.active(),
                scheduled.nextOccurrence(),
                scheduled.history().timesRaised(),
                scheduled.history().lastRaisedOn(),
                schedule.createdAt());
    }
}
