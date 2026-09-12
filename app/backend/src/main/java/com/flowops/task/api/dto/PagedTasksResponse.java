package com.flowops.task.api.dto;

import java.util.List;

public record PagedTasksResponse(
        String section, List<TaskSummaryResponse> rows, int page, int size, int total, int totalPages) {}
