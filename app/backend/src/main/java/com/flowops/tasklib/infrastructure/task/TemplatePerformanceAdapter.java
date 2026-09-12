package com.flowops.tasklib.infrastructure.task;

import com.flowops.task.application.published.TemplateUsageUseCase;
import com.flowops.tasklib.application.exception.UnknownBandException;
import com.flowops.tasklib.application.port.TemplatePerformancePort;
import com.flowops.tasklib.domain.TemplatePerformance;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class TemplatePerformanceAdapter implements TemplatePerformancePort {
    private final TemplateUsageUseCase usage;

    public TemplatePerformanceAdapter(TemplateUsageUseCase usage) {
        this.usage = usage;
    }

    @Override
    public TemplatePerformance performanceOf(UUID templateId) {
        TemplateUsageUseCase.Usage read = usage.of(templateId);
        return new TemplatePerformance(
                read.stamped(),
                read.active().measured(),
                read.active().medianSeconds(),
                read.active().lowerQuartileSeconds(),
                read.active().upperQuartileSeconds(),
                read.approval().passedFirstTime(),
                read.approval().reviewed());
    }

    @Override
    public LiveWork liveWorkFor(UUID templateId) {
        TemplateUsageUseCase.LiveCounts live = usage.of(templateId).live();
        return new LiveWork(
                live.notStarted(), live.running(), live.blocked(), live.inReview(), live.finished(), live.overdue());
    }

    @Override
    public List<Row> tasksIn(UUID templateId, String band) {
        TemplateUsageUseCase.LiveBand named;
        try {
            named = TemplateUsageUseCase.LiveBand.valueOf(
                    band.toUpperCase(Locale.ROOT).replace('-', '_'));
        } catch (IllegalArgumentException unknown) {
            throw new UnknownBandException(band);
        }

        return usage.tasksIn(templateId, named).stream()
                .map(row -> new Row(
                        row.taskId(), row.title(), row.assigneeId(), row.assigneeName(), row.state(), row.deadline()))
                .toList();
    }
}
