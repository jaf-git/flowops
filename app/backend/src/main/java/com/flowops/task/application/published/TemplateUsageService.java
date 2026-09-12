package com.flowops.task.application.published;

import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TemplateUsageService implements TemplateUsageUseCase {
    private final TemplateUsageReadPort usage;
    private final Clock clock;

    public TemplateUsageService(TemplateUsageReadPort usage, Clock clock) {
        this.usage = usage;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Usage of(UUID templateId) {
        return usage.usageOf(templateId, clock.instant());
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> stampedTaskIds(UUID templateId) {
        return usage.stampedTaskIds(templateId);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TaskRow> tasksIn(UUID templateId, LiveBand band) {
        return usage.rowsOf(templateId, band, clock.instant());
    }
}
