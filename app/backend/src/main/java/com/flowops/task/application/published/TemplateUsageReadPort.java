package com.flowops.task.application.published;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface TemplateUsageReadPort {
    TemplateUsageUseCase.Usage usageOf(UUID templateId, Instant now);

    List<UUID> stampedTaskIds(UUID templateId);

    List<TemplateUsageUseCase.TaskRow> rowsOf(UUID templateId, TemplateUsageUseCase.LiveBand band, Instant now);
}
