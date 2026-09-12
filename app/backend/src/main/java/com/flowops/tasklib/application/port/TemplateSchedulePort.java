package com.flowops.tasklib.application.port;

import com.flowops.tasklib.domain.TemplateSchedule;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface TemplateSchedulePort {
    void save(TemplateSchedule schedule);

    Optional<TemplateSchedule> byId(UUID id);

    List<TemplateSchedule> forTemplate(UUID templateId);

    List<TemplateSchedule> active();

    boolean hasRaised(UUID scheduleId, LocalDate occurrence);

    void recordRun(UUID scheduleId, LocalDate occurrence, UUID taskId, Instant now);

    History historyOf(UUID scheduleId);

    record History(int timesRaised, LocalDate lastRaisedOn) {}
}
