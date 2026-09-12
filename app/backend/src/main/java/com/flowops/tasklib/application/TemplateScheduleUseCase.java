package com.flowops.tasklib.application;

import com.flowops.tasklib.application.port.TemplateSchedulePort;
import com.flowops.tasklib.domain.Recurrence;
import com.flowops.tasklib.domain.TemplateSchedule;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface TemplateScheduleUseCase {
    Scheduled set(UUID templateId, UUID assigneeId, Recurrence recurrence);

    List<Scheduled> forTemplate(UUID templateId);

    Scheduled pause(UUID scheduleId);

    Scheduled resume(UUID scheduleId);

    java.util.Optional<UUID> raise(UUID scheduleId, LocalDate occurrence);

    record Scheduled(TemplateSchedule schedule, TemplateSchedulePort.History history, LocalDate nextOccurrence) {}
}
