package com.flowops.task.api.dto;

import java.util.List;

public record TasksResponse(List<TaskSummaryResponse> tasks) {}
