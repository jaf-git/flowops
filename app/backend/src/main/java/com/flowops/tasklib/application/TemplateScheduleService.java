package com.flowops.tasklib.application;

import com.flowops.tasklib.application.exception.ScheduleNotFoundException;
import com.flowops.tasklib.application.exception.TemplateNotFoundException;
import com.flowops.tasklib.application.port.IdentifyCallerPort;
import com.flowops.tasklib.application.port.RaiseScheduledTaskPort;
import com.flowops.tasklib.application.port.TaskTemplatePort;
import com.flowops.tasklib.application.port.TemplateSchedulePort;
import com.flowops.tasklib.domain.Recurrence;
import com.flowops.tasklib.domain.TaskTemplate;
import com.flowops.tasklib.domain.TemplateDetails;
import com.flowops.tasklib.domain.TemplateSchedule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateScheduleService implements TemplateScheduleUseCase {
    private static final Logger LOG = LoggerFactory.getLogger(TemplateScheduleService.class);

    private final TemplateSchedulePort schedules;
    private final TaskTemplatePort templates;
    private final RaiseScheduledTaskPort tasks;
    private final IdentifyCallerPort caller;
    private final Clock clock;

    public TemplateScheduleService(
            TemplateSchedulePort schedules,
            TaskTemplatePort templates,
            RaiseScheduledTaskPort tasks,
            IdentifyCallerPort caller,
            Clock clock) {
        this.schedules = schedules;
        this.templates = templates;
        this.tasks = tasks;
        this.caller = caller;
        this.clock = clock;
    }

    @Override
    @Transactional
    public Scheduled set(UUID templateId, UUID assigneeId, Recurrence recurrence) {
        loadTemplate(templateId).requireUsable();

        TemplateSchedule schedule =
                TemplateSchedule.set(templateId, assigneeId, requireCaller(), recurrence, clock.instant());
        schedules.save(schedule);
        return described(schedule);
    }

    @Override
    @Transactional(readOnly = true)
    public List<Scheduled> forTemplate(UUID templateId) {
        loadTemplate(templateId);
        return schedules.forTemplate(templateId).stream().map(this::described).toList();
    }

    @Override
    @Transactional
    public Scheduled pause(UUID scheduleId) {
        return saved(loadSchedule(scheduleId).paused(clock.instant()));
    }

    @Override
    @Transactional
    public Scheduled resume(UUID scheduleId) {
        TemplateSchedule schedule = loadSchedule(scheduleId);
        loadTemplate(schedule.templateId()).requireUsable();
        return saved(schedule.resumed(clock.instant()));
    }

    @Override
    @Transactional
    public Optional<UUID> raise(UUID scheduleId, LocalDate occurrence) {
        TemplateSchedule schedule = loadSchedule(scheduleId);
        if (schedules.hasRaised(scheduleId, occurrence)) {
            return Optional.empty();
        }

        if (!schedule.fallsDueOn(occurrence, clock.getZone())) {
            return Optional.empty();
        }

        TaskTemplate template = loadTemplate(schedule.templateId());
        template.requireUsable();

        TemplateDetails details = template.details();
        UUID taskId = tasks.raise(new RaiseScheduledTaskPort.ScheduledTask(
                details.title(),
                details.description(),
                schedule.assigneeId(),
                schedule.createdBy(),
                null,
                details.priority(),
                schedule.templateId(),
                details.estimatedHours()));

        schedules.recordRun(scheduleId, occurrence, taskId, clock.instant());

        templates.recordUse(schedule.templateId());

        LOG.info("schedule {} raised task {} for {}", scheduleId, taskId, occurrence);
        return Optional.of(taskId);
    }

    private Scheduled saved(TemplateSchedule schedule) {
        schedules.save(schedule);
        return described(schedule);
    }

    private Scheduled described(TemplateSchedule schedule) {
        return new Scheduled(schedule, schedules.historyOf(schedule.id()), schedule.nextOccurrenceOnOrAfter(today()));
    }

    private LocalDate today() {
        return LocalDate.now(clock);
    }

    private TaskTemplate loadTemplate(UUID id) {
        return templates.byId(id).orElseThrow(() -> new TemplateNotFoundException("no such template"));
    }

    private TemplateSchedule loadSchedule(UUID id) {
        return schedules.byId(id).orElseThrow(() -> new ScheduleNotFoundException("no such schedule"));
    }

    private UUID requireCaller() {
        return caller.currentCaller()
                .orElseThrow(() -> new IllegalStateException("there is no session behind this call"));
    }
}
