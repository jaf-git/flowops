package com.flowops.tasklib.api.dto;

import com.flowops.tasklib.application.port.TemplatePerformancePort;
import java.time.Instant;
import java.util.UUID;

public record TemplateTaskResponse(
        UUID taskId, String title, UUID assigneeId, String assigneeName, String state, Instant deadline) {
    public static TemplateTaskResponse of(TemplatePerformancePort.Row row) {
        return new TemplateTaskResponse(
                row.taskId(), row.title(), row.assigneeId(), row.assigneeName(), row.state(), row.deadline());
    }
}
