package com.flowops.tasklib.application.sweep;

import com.flowops.tasklib.application.TemplateScheduleUseCase;
import com.flowops.tasklib.application.port.TemplateSchedulePort;
import com.flowops.tasklib.domain.TemplateSchedule;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ScheduledWorkSweep {
    private static final Logger LOG = LoggerFactory.getLogger(ScheduledWorkSweep.class);

    private final TemplateSchedulePort schedules;
    private final TemplateScheduleUseCase raising;
    private final Clock clock;

    public ScheduledWorkSweep(TemplateSchedulePort schedules, TemplateScheduleUseCase raising, Clock clock) {
        this.schedules = schedules;
        this.raising = raising;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${flowops.tasklib.schedule-sweep-ms:300000}")
    public void raiseWorkDueToday() {
        LocalDate today = LocalDate.now(clock);
        List<TemplateSchedule> running = schedules.active();
        int raised = 0;

        for (TemplateSchedule schedule : running) {
            if (!schedule.fallsDueOn(today, clock.getZone())) {
                continue;
            }
            try {
                raised += raising.raise(schedule.id(), today).isPresent() ? 1 : 0;
            } catch (RuntimeException failure) {
                LOG.warn("schedule {} could not raise its occurrence for {}", schedule.id(), today, failure);
            }
        }

        if (raised > 0) {
            LOG.info("raised {} task(s) from {} running schedule(s) for {}", raised, running.size(), today);
        }
    }
}
