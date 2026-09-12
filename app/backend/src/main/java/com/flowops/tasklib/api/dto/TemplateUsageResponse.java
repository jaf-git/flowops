package com.flowops.tasklib.api.dto;

import com.flowops.tasklib.application.port.TemplatePerformancePort;
import com.flowops.tasklib.domain.TemplatePerformance;

public record TemplateUsageResponse(
        int stamped,
        java.math.BigDecimal estimatedHours,
        Long medianActiveSeconds,
        Long lowerQuartileSeconds,
        Long upperQuartileSeconds,
        int measuredTasks,
        boolean tooFewToAverage,
        boolean varyWidely,
        int passedFirstTime,
        int reviewed,
        LiveWorkResponse live) {
    public record LiveWorkResponse(int notStarted, int running, int blocked, int inReview, int finished, int overdue) {}

    public static TemplateUsageResponse of(
            TemplatePerformance performance,
            TemplatePerformancePort.LiveWork live,
            java.math.BigDecimal estimatedHours) {
        return new TemplateUsageResponse(
                performance.stamped(),
                estimatedHours,
                performance.medianActiveSeconds(),
                performance.lowerQuartileSeconds(),
                performance.upperQuartileSeconds(),
                performance.measuredTasks(),
                performance.tooFewToAverage(),
                performance.varyWidely(),
                performance.passedFirstTime(),
                performance.reviewed(),
                new LiveWorkResponse(
                        live.notStarted(),
                        live.running(),
                        live.blocked(),
                        live.inReview(),
                        live.finished(),
                        live.overdue()));
    }
}
