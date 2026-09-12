package com.flowops.task.api.dto;

import java.util.List;

public record TaskMaterialResponse(List<TaskLinkResponse> links, List<ChecklistItemResponse> checklist) {}
