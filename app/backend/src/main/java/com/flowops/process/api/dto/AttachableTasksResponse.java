package com.flowops.process.api.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record AttachableTasksResponse(List<Row> tasks) {
    public record Row(UUID id, String title, String state, UUID assigneeId, Instant deadline) {}
}
