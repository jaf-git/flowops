package com.flowops.process.api.dto;

import java.util.UUID;

public record InstanceSummaryResponse(
        UUID id,
        String name,
        String templateName,
        String state,
        ProgressResponse progress,
        int awaitingAssignmentCount) {}
